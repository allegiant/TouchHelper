plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "org.eu.freex.app"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "org.eu.freex.app"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation(libs.androidx.appcompat)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// ==========================================
// 🦀 Rust 自动化构建配置
// ==========================================

val buildRust = tasks.register<Exec>("buildRust") {
    group = "build"
    description = "Builds the Rust core library using cargo-ndk"

    // 1. 🔥 修正 rust_core 路径查找逻辑
    // 如果你在 AS 打开的是 'android' 目录，需要用 parentFile 往上跳一级
    val gradleRoot = rootProject.rootDir
    var rustDir = gradleRoot.resolve("rust_core") // 先试探默认位置

    if (!rustDir.exists()) {
        // 如果找不到，说明可能是嵌套结构，往上找一级
        rustDir = gradleRoot.parentFile.resolve("rust_core")
    }

    if (!rustDir.exists()) {
        throw GradleException("❌ 找不到 rust_core 目录！请检查文件夹结构。\n搜索路径: $rustDir")
    }

    val jniLibsDir = project.file("src/main/jniLibs")

    // 2. 准备 Cargo 路径
    val homeDir = File(System.getProperty("user.home"))
    val cargoBinDir = homeDir.resolve(".cargo/bin")
    val cargoExe = cargoBinDir.resolve("cargo.exe")

    // 3. 注入环境变量 (关键：让 cargo 能找到 cargo-ndk)
    // 同时也把 PATH 补全，防止 cmd 找不到
    val currentPath = System.getenv("PATH") ?: System.getenv("Path") ?: ""
    environment("PATH", cargoBinDir.absolutePath + File.pathSeparator + currentPath)

    // 设置正确的工作目录
    workingDir = rustDir

    // 4. 执行命令 (去掉 cmd /c，直接调用 exe 通常更稳，前提是 PATH 设对了)
    // 如果 cargo.exe 存在就用绝对路径，否则尝试直接用 "cargo"
    val executable = if (cargoExe.exists()) cargoExe.absolutePath else "cargo"

    commandLine(executable, "ndk",
        "-t", "arm64-v8a",
        "-t", "armeabi-v7a",
        "-t", "x86_64",
        "-o", jniLibsDir.absolutePath,
        "build", "--release"
    )

    doLast {
        println("✅ Rust Core compiled successfully at: ${rustDir.absolutePath}")
    }
}


// ==========================================
// 🔗 依赖钩子：把所有任务串起来
// ==========================================

tasks.named("preBuild") {
    // 1. 让 App 编译前，先编译 Rust
    dependsOn(buildRust)

    // 2. 同时也依赖 Server (之前配置的)
    if (rootProject.findProject(":server") != null) {
        dependsOn(":server:buildDex")
    }
}

// (可选) 增加一个清理任务：运行 clean 时删除生成的 .so 文件
tasks.named("clean") {
    doLast {
        val jniLibsDir = project.file("src/main/jniLibs")
        if (jniLibsDir.exists()) {
            delete(jniLibsDir)
            println("🧹 Cleaned up jniLibs directory.")
        }
    }
}

// 🔥 关键配置：让 App 编译前先编译 Server
// ==========================================

// 1. 判断 :server 模块是否存在 (防止以后移除模块报错)
if (rootProject.findProject(":server") != null) {

    // 2. 获取 App 的 preBuild 任务 (这是 Android 构建的最开始)
    tasks.named("preBuild") {
        // 3. 声明依赖关系：必须先执行 :server 模块的 buildDex 任务
        dependsOn(":server:buildDex")
    }

    println("🔗 已链接依赖: app:preBuild -> server:buildDex")
}