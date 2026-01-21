import gobley.gradle.GobleyHost
import gobley.gradle.cargo.dsl.android
import gobley.gradle.cargo.dsl.jvm
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    kotlin("plugin.atomicfu") version libs.versions.kotlin
    alias { libs.plugins.gobley.cargo }
    alias { libs.plugins.gobley.uniffi }
    alias(libs.plugins.kotlin.serialization)
    // alias(libs.plugins.composeMultiplatform) //如果你在这个层级不需要写UI，建议注释掉，减少编译时间
}
cargo {
    packageDirectory = file("../../core")
    builds.android {
    }
    builds.jvm {
        // 逻辑含义：只有当 "构建目标" 等于 "当前电脑架构" 时，才进行构建和嵌入
        // 这样在 Windows 上就只会编 Windows 版，在 Mac 上只编 Mac 版
        embedRustLibrary = (rustTarget == GobleyHost.current.rustTarget)
    }
}


kotlin {
    // 1. Android 目标
    androidTarget {
        publishLibraryVariants("release", "debug")
    }

    // 2. Windows/JVM 目标
    jvm("desktop") {
        // 新写法
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                // 如果你需要在这个层写通用的 Kotlin 逻辑
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation(libs.jna)
                implementation(libs.kotlinx.serialization.json)
            }
            // 👉 【必须加这一行】告诉 Gradle：这里的代码也是我的源码，请编译它！
            kotlin.srcDir("build/generated/uniffi/src")
        }

        androidMain {
            dependencies {
                implementation(libs.jna)
            }
        }

        val desktopMain by getting {
            dependencies {
                implementation(libs.jna)
            }
        }
    }
}




android {
    namespace = "org.eu.freex.bridge"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        // 确保包含你的 Rust 支持的架构
        ndk {
            abiFilters.add("arm64-v8a")
            abiFilters.add("x86_64") // 如果你需要
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    sourceSets.getByName("main") {
        manifest.srcFile("src/androidMain/AndroidManifest.xml")
        res.srcDirs("src/androidMain/res")
    }
}

// ==========================================================================
// 🦀 Rust 集成构建逻辑 (这是中间层的灵魂)
// ==========================================================================

// 定义 core 的相对路径 (根据你的 Monorepo 结构)
// 假设结构: Root -> rust-bridge-sdk -> shared
// 所以 core 在: ../../core
val rustBasePath = file("../../core")
val libName = "touch_core" // ⚠️ 必须与 Cargo.toml 中的 [lib] name 一致

// 1. Android 构建任务 (调用 cargo-ndk)
/*val buildRustAndroid = tasks.register<Exec>("buildRustAndroid") {
    group = "rust"
    description = "Build Rust code for Android using cargo-ndk"
    workingDir = rustBasePath

    // 输出路径：直接输出到模块的 jniLibs 目录，这样会被自动打包进 AAR
    val jniLibsDir = file("src/androidMain/jniLibs")

    // 命令行：同时构建 arm64 和 x86
    commandLine(
        "cargo", "ndk",
        "-t", "arm64-v8a",
        "-t", "x86_64",
        "-o", jniLibsDir.absolutePath,
        "build", "--release"
    )
}*/

// 2. Desktop (Windows) 构建任务
val buildRustDesktop = tasks.register<Exec>("buildRustDesktop") {
    group = "rust"
    description = "Build Rust code for Windows"
    workingDir = rustBasePath

    // Windows 构建 (假设在 Windows 环境下运行)
    commandLine("cargo", "build", "--release")

    // 修复 Config Cache: 在配置阶段解析路径 (File 对象可序列化)
    val sourceDll = file("$rustBasePath/target/release/$libName.dll")
    // 注意：这里改为了 jvmMain 以匹配上面的 sourceSet
    // ⚠️ 关键步骤：将编译好的 DLL 复制到 resources 目录
    // 这样它会被打包进 JAR 文件，供 consumers (FreexTools) 提取使用
    val targetDir = file("src/desktopMain/resources/win32-x86-64")

    doLast {
        // 修复 Config Cache: 使用 Java IO 替代 project.copy
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        if (sourceDll.exists()) {
            // 使用 Java 标准 API 复制文件
            sourceDll.copyTo(File(targetDir, sourceDll.name), overwrite = true)
        }
    }
}

// 3. 生成 Kotlin 接口 (Uniffi Bindgen)
/*val generateBindings = tasks.register<Exec>("generateBindings") {
    group = "rust"
    description = "Generate Kotlin bindings using uniffi-bindgen"
    workingDir = rustBasePath

    // 我们需要指向一个已编译的库文件来生成绑定。
    // 这里指向 Windows 的 release dll 即可 (接口定义是跨平台的)
    val libraryFile = file("$rustBasePath/target/release/$libName.dll")
    val outDir = layout.buildDirectory.dir("generated/uniffi/src").get().asFile

    // 只有当 DLL 存在时才运行生成，避免报错 (首次运行可能需要先 buildRustDesktop)
    onlyIf { libraryFile.exists() }

    commandLine(
        "cargo", "run", "--bin", "uniffi-bindgen",
        "generate", "--library", libraryFile,
        "--language", "kotlin",
        "--out-dir", outDir.absolutePath
    )

    // 让这个任务依赖于构建任务，确保库文件存在
    dependsOn(buildRustDesktop)
}*/

// 4. 任务挂载
afterEvaluate {
    // 使用 afterEvaluate 确保 Android 任务已创建
    tasks.named("preBuild") {
        //dependsOn(buildRustAndroid)
        dependsOn(buildRustDesktop)
    }
}

// 确保生成代码任务在编译 Kotlin 之前执行
/*
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    dependsOn(generateBindings)
}*/
