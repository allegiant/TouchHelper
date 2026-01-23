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
    builds.android {}
    builds.jvm {
        embedRustLibrary = (rustTarget == GobleyHost.current.rustTarget)
    }
}


kotlin {
    // 1. Android 目标
    androidTarget {
        publishLibraryVariants("release", "debug")
    }

    // 2. Windows/JVM 目标
    jvm {
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
                compileOnly(libs.jna)
                implementation(libs.kotlinx.serialization.json)
            }
        }

        androidMain {
            dependencies {
                implementation("net.java.dev.jna:jna:5.13.0@aar")
            }
        }
        jvmMain {
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

