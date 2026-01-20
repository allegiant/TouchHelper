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

// 1. 路径计算
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
    val latest = buildToolsDir.list()?.filter { !it.contains("rc") }?.maxOrNull() ?: return ""
    return "$sdkPath/build-tools/$latest"
}

val computedSdkPath = getAndroidSdkPath(project)
val computedBuildToolsPath = getBuildToolsPath(computedSdkPath, project)
val computedAndroidJar = if (computedSdkPath.isNotEmpty()) "$computedSdkPath/platforms/android-34/android.jar" else ""

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
    if (computedAndroidJar.isNotEmpty()) {
        compileOnly(files(computedAndroidJar))
    }
}

// 2. 任务定义
abstract class BuildDexTask : DefaultTask() {
    @get:Inject abstract val execOps: ExecOperations
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val inputJar: RegularFileProperty
    @get:Input abstract val androidJarPath: Property<String>
    @get:Input abstract val d8Path: Property<String>
    @get:OutputFile abstract val outputJar: RegularFileProperty
    @get:Internal abstract val tempDir: DirectoryProperty

    @TaskAction
    fun run() {
        val workDir = tempDir.get().asFile
        if (!workDir.exists()) workDir.mkdirs()

        // D8: Jar -> Dex
        execOps.exec {
            workingDir = workDir
            commandLine(d8Path.get(), "--lib", androidJarPath.get(), "--output", ".", "--min-api", "26", inputJar.get().asFile.absolutePath)
        }
        // Jar: Dex -> Server.jar
        execOps.exec {
            workingDir = workDir
            commandLine("jar", "cf", "final_server.jar", "classes.dex")
        }
        // Deploy
        workDir.resolve("final_server.jar").copyTo(outputJar.get().asFile, overwrite = true)
        println("✅ Server built at: ${outputJar.get().asFile.absolutePath}")
    }
}

val buildDex by tasks.registering(BuildDexTask::class) {
    group = "build"
    val jarTask = tasks.named<Jar>("jar").get()
    dependsOn(jarTask)
    inputJar.set(jarTask.archiveFile)

    // 🌟🌟🌟【核心修改】🌟🌟🌟
    // 不再输出到自己的 build 目录，而是直接输出到 App 的 assets 源码目录
    // 这样 App 就不需要去别的地方找文件了
    outputJar.set(rootProject.file("FreeToucher/app/src/main/assets/server.jar"))

    tempDir.set(layout.buildDirectory.dir("dex_temp"))

    if (computedAndroidJar.isNotEmpty() && computedBuildToolsPath.isNotEmpty()) {
        androidJarPath.set(computedAndroidJar)
        val isWindows = Os.isFamily(Os.FAMILY_WINDOWS)
        val d8Name = if (isWindows) "d8.bat" else "d8"
        d8Path.set(file("$computedBuildToolsPath/$d8Name").absolutePath)
    } else {
        enabled = false
    }
}

// ==================================================================
// 🦀 新增：自动生成常量的任务
// ==================================================================
val generateConstants by tasks.registering(Exec::class) {
    group = "rust"
    description = "Generates shared constants from Rust core"

    // 1. 定位到 core 目录 (根据你的项目结构调整 ../../core)
    val coreDir = file("../core")
    workingDir = coreDir

    // 2. 动态计算目标文件的绝对路径
    // layout.projectDirectory 是当前 FreeToucherServer 的目录
    val targetFile = layout.projectDirectory.file("src/main/java/bind/GeneratedConstants.java").asFile.absolutePath

    // 3. 传给 Rust: cargo run --bin  gen_java_constants -- <目标路径>
    // 注意: "--" 是告诉 cargo 后面的参数是传给程序的，不是传给 cargo 的
    commandLine("cargo", "run", "--bin", "gen_java_constants", "--", targetFile)

    // 增量构建支持 (输入没变就不重跑)
    inputs.files(fileTree(coreDir.resolve("src")) { include("**/*.rs") })
    outputs.file(targetFile)
}

// 4. 关键：让 Java 编译任务依赖它
// 这样每次 compileJava 之前，都会先跑 generateConstants
tasks.named("compileJava") {
    dependsOn(generateConstants)
}