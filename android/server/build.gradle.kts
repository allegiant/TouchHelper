import java.util.Properties

plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

// --- 1. 自动寻找 SDK 和 BuildTools 的辅助函数 ---

fun getAndroidSdkPath(): String {
    val localPropsFile = project.rootProject.file("local.properties")
    if (localPropsFile.exists()) {
        val props = Properties()
        localPropsFile.inputStream().use { props.load(it) }
        val sdkDir = props.getProperty("sdk.dir")
        if (sdkDir != null) return sdkDir
    }
    return System.getenv("ANDROID_HOME") ?: throw GradleException("Android SDK not found! Check local.properties.")
}

fun getBuildToolsPath(sdkPath: String): String {
    val buildToolsDir = file("$sdkPath/build-tools")
    if (!buildToolsDir.exists()) throw GradleException("Build Tools folder not found at $buildToolsDir")

    // 找版本号最大的文件夹 (过滤掉 rc 预览版)
    val latest = buildToolsDir.list()
        ?.filter { !it.contains("rc") }
        ?.maxOrNull()
        ?: throw GradleException("No installed build-tools found.")

    return "$sdkPath/build-tools/$latest"
}

val sdkPath = getAndroidSdkPath()

dependencies {
    // 引入 Gson 处理 JSON
    implementation("com.google.code.gson:gson:2.13.2")

    // 编译时引入 android.jar (为了使用 Log 等类)
    // 只要你的 SDK 里有 android-30 以上的任意版本都行，这里动态指向 android-34
    compileOnly(files("$sdkPath/platforms/android-34/android.jar"))
}

// --- 2. 打包标准 Jar (包含依赖) ---

tasks.named<Jar>("jar") {
    manifest {
        // 🔥 这里对应你的 Java 类名 (没有 Kt 后缀)
        attributes["Main-Class"] = "org.eu.freex.server.Main"
    }

    // 将依赖 (如 Gson) 打入 Jar 包 (Fat Jar)
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// --- 3. 核心任务：生成 Dex 并注入 Jar ---

val buildDex = tasks.register("buildDex") {
    group = "build"
    description = "Compiles Java bytecode to Android Dex format"

    // 必须等待 jar 任务完成
    dependsOn("jar")

    // 定义输入输出
    val inputJarFile = tasks.named<Jar>("jar").get().archiveFile.get().asFile
    val outputDir = layout.buildDirectory.dir("libs").get().asFile

    doLast {
        val buildToolsPath = getBuildToolsPath(sdkPath)
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        val d8 = "$buildToolsPath/${if (isWindows) "d8.bat" else "d8"}"
        val androidJar = "$sdkPath/platforms/android-34/android.jar"

        println("👉 开始生成 Dex 文件...")

        // 步骤 A: 调用 d8 将 jar 里的 class 转为 classes.dex
        exec {
            workingDir = outputDir
            // --output . 表示在当前目录生成 classes.dex
            commandLine(d8, "--lib", androidJar, "--output", ".", inputJarFile.absolutePath)
        }

        println("👉 Dex 生成成功，正在合并进 final_server.jar ...")

        // 步骤 B: 将生成的 classes.dex 打包进一个新的 Jar
        // 注意：Android 的 CLASSPATH 加载只认包含 classes.dex 的 jar
        exec {
            workingDir = outputDir
            // 使用 jar 命令创建新包 (前提是环境变量里有 java)
            commandLine("jar", "cf", "final_server.jar", "classes.dex")
        }

        println("👉 正在部署到 App Assets ...")

        // 步骤 C: 复制到 app 模块
        copy {
            from(outputDir.resolve("final_server.jar"))
            into(project.rootProject.file("app/src/main/assets")) // 👈 注意这里是指向 app 模块的路径
            rename("final_server.jar", "server.jar")
        }

        println("✅ Server 构建完成！已更新到 app/assets/server.jar")
    }
}

// 让 assemble 任务依赖 buildDex
// 这样每次点 Android Studio 的 "Run" (绿三角) 时，都会触发这个流程
tasks.named("assemble") {
    dependsOn(buildDex)
}

// (可选) 增加一个清理任务：运行 clean 时删除生成的 .so 文件
tasks.named("clean") {
    doLast {
        val bindConstantsPath = project.rootProject.file("server/src/main/java/bind")
        val serverJarPath = project.rootProject.file("app/src/main/assets/server.jar")
        if (bindConstantsPath.exists()) {
            delete(bindConstantsPath)
            println("🧹 Cleaned up GeneratedConstants.")
        }
        if (serverJarPath.exists()) {
            delete(serverJarPath)
            println("🧹 Cleaned up server.jar.")
        }
    }
}