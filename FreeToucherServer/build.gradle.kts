import java.util.Properties
import org.apache.tools.ant.taskdefs.condition.Os
import javax.inject.Inject

plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

// --- 1. 路径计算 (配置阶段执行) ---

fun getAndroidSdkPath(project: Project): String {
    val envHome = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    if (!envHome.isNullOrEmpty()) return envHome

    val localPropsFile = project.rootProject.file("local.properties")
    if (localPropsFile.exists()) {
        val props = Properties()
        localPropsFile.inputStream().use { props.load(it) }
        val sdkDir = props.getProperty("sdk.dir")
        if (!sdkDir.isNullOrEmpty()) return sdkDir
    }
    return ""
}

fun getBuildToolsPath(sdkPath: String, project: Project): String {
    if (sdkPath.isEmpty()) return ""
    val buildToolsDir = project.file("$sdkPath/build-tools")
    if (!buildToolsDir.exists()) return ""

    val latest = buildToolsDir.list()
        ?.filter { !it.contains("rc") }
        ?.maxOrNull()
        ?: return ""

    return "$sdkPath/build-tools/$latest"
}

// 变量重命名，避免与 Task 属性冲突
val computedSdkPath = getAndroidSdkPath(project)
val computedBuildToolsPath = getBuildToolsPath(computedSdkPath, project)
val computedAndroidJar = if (computedSdkPath.isNotEmpty()) "$computedSdkPath/platforms/android-34/android.jar" else ""
val computedAppAssetsDir = project.rootProject.file("FreeToucher/app/src/main/assets")

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
    if (computedAndroidJar.isNotEmpty()) {
        compileOnly(files(computedAndroidJar))
    }
}

// --- 2. 定义抽象任务类 ---

abstract class BuildDexTask : DefaultTask() {

    // 🌟 修复：改为 abstract val，让 Gradle 自动注入实现
    @get:Inject
    abstract val execOps: ExecOperations

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputJar: RegularFileProperty

    @get:Input
    abstract val androidJarPath: Property<String>

    @get:Input
    abstract val d8Path: Property<String>

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @get:Internal
    abstract val tempDir: DirectoryProperty

    @TaskAction
    fun run() {
        val d8 = d8Path.get()
        val libJar = androidJarPath.get()
        val jarFile = inputJar.get().asFile
        val outFile = outputJar.get().asFile

        val workDir = tempDir.get().asFile
        if (!workDir.exists()) workDir.mkdirs()

        println("👉 [1/3] Converting JAR to DEX using D8...")

        // 使用注入的 execOps
        execOps.exec {
            workingDir = workDir
            commandLine(
                d8,
                "--lib", libJar,
                "--output", ".",
                "--min-api", "26",
                jarFile.absolutePath
            )
        }

        val dexFile = workDir.resolve("classes.dex")
        if (!dexFile.exists()) {
            throw GradleException("❌ Dex generation failed: classes.dex not found.")
        }

        println("👉 [2/3] Packaging classes.dex into final jar...")

        execOps.exec {
            workingDir = workDir
            commandLine("jar", "cf", "final_server.jar", "classes.dex")
        }

        val tempJar = workDir.resolve("final_server.jar")

        println("👉 [3/3] Deploying to App Assets: ${outFile.absolutePath}")

        if (!outFile.parentFile.exists()) outFile.parentFile.mkdirs()
        tempJar.copyTo(outFile, overwrite = true)

        println("✅ Server built successfully.")
    }
}

// --- 3. 注册并配置任务 ---

val buildDex by tasks.registering(BuildDexTask::class) {
    group = "build"
    description = "Compiles Java/Kotlin to Dex and packages it into server.jar"

    val jarTask = tasks.named<Jar>("jar").get()

    dependsOn(jarTask)
    inputJar.set(jarTask.archiveFile)
    outputJar.set(computedAppAssetsDir.resolve("server.jar"))

    tempDir.set(layout.buildDirectory.dir("dex_temp"))

    if (computedAndroidJar.isNotEmpty() && computedBuildToolsPath.isNotEmpty()) {
        androidJarPath.set(computedAndroidJar)

        val isWindows = Os.isFamily(Os.FAMILY_WINDOWS)
        val d8Name = if (isWindows) "d8.bat" else "d8"
        val d8File = file("$computedBuildToolsPath/$d8Name")
        d8Path.set(d8File.absolutePath)
    } else {
        androidJarPath.set("")
        d8Path.set("")
        enabled = false
        println("⚠️ Android SDK or Build Tools not found. buildDex task disabled.")
    }
}

// --- 4. 挂载到生命周期 ---

tasks.named("assemble") {
    dependsOn(buildDex)
}

tasks.named<Delete>("clean") {
    delete(computedAppAssetsDir.resolve("server.jar"))
}

// ... (保留上面的 buildDex 定义) ...

// =========================================================
// 🔌 自动装配：主动将 Server 构建挂载到 App 的生命周期
// =========================================================
val appProjectName = ":FreeToucher" // 确保这里也是 :FreeToucher

rootProject.findProject(appProjectName)?.let { appProject ->
    println("🔗 [FreeToucherServer] Found App module, injecting dependencies...")

    // ❌ 删除 appProject.afterEvaluate { ... } 包裹
    // ✅ 直接使用 appProject.tasks.configureEach (它能自动处理已加载/未加载两种情况)

    appProject.tasks.configureEach {
        val taskName = name

        // 1. 常规构建 (preBuild)
        // 2. 资源合并 (mergeAssets): 确保在合并资源前 server.jar 已就位
        // 3. Lint 检查 (lintVital): 解决你之前遇到的 lint 报错
        if (taskName == "preBuild" ||
            (taskName.contains("merge") && taskName.contains("assets")) ||
            taskName.contains("lint")) {

            // 让 App 的这些任务依赖本模块的 buildDex 任务
            dependsOn(buildDex)

            // 可选：打印日志调试
            // println("   ➕ Injected dependency: :${appProject.name}:$taskName -> :FreeToucherServer:buildDex")
        }
    }
} ?: println("⚠️ [FreeToucherServer] Warning: App module '$appProjectName' not found. Dependency injection skipped.")