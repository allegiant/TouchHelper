// 文件: FreeToucherServer/build.gradle.kts
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
    // 引入 Gson 处理 JSON
    implementation("com.google.code.gson:gson:2.13.2")

    // 编译时引入 android.jar (为了使用 Log 等类)
    // 只要你的 SDK 里有 android-30 以上的任意版本都行，这里动态指向 android-34
    compileOnly(files(computedAndroidJar))
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
    }
}

val buildDex by tasks.registering(BuildDexTask::class) {
    group = "build"
    val jarTask = tasks.named<Jar>("jar").get()
    dependsOn(jarTask)
    inputJar.set(jarTask.archiveFile)
    // 输出到 build/generated/assets/server/server.jar
    outputJar.set(layout.buildDirectory.file("generated/assets/server/server.jar"))
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

// 3. 【关键】创建一个 Configuration 管道，把文件暴露出去
val serverAssetConfig by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    // 把 buildDex 的输出结果放入管道
    add(serverAssetConfig.name, buildDex.map { it.outputJar })
}