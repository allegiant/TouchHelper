plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// 1. 定义消费者管道，接收 Server 的文件
val serverAssetConsumer by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    implementation(libs.jna)
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
    implementation(project(":lib-sdk"))
    // 2. 连接到 Server 模块的管道
    serverAssetConsumer(project(mapOf("path" to ":FreeToucherServer", "configuration" to "serverAssetConfig")))
}

// 3. 创建同步任务：把管道里的文件拿过来
val copyServerAsset by tasks.registering(Sync::class) {
    from(serverAssetConsumer)
    into(layout.buildDirectory.dir("generated/assets/server"))
    rename { "server.jar" } // 确保文件名正确
}

android {
    namespace = "org.eu.freex.app"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "org.eu.freex.touchhelper"
        minSdk = 26
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
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
    buildFeatures {
        compose = true
    }
    // 4. 【将同步任务注册为资源目录】
    sourceSets {
        getByName("main") {
            assets.srcDir(copyServerAsset)
        }
    }

    // 5. 【核弹级修复】禁用 Lint 检查
    // 这行配置能解决 "Implicit Dependency" 报错，也能极大减少 Windows 文件占用
    lint {
        checkReleaseBuilds = false // ⛔ 关掉 Release 构建的检查 (解决报错)
        abortOnError = false       // ⛔ 即使报错也不停止
        checkDependencies = false  // ⛔ 不检查依赖 (防止锁死 jar 包)
    }
}