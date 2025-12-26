plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
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
}

android {
    namespace = "org.eu.freex.app"

    // 🌟 修正点 1：使用标准的整数版本号 (改为 34 或 35)
    compileSdk = 36

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
}

tasks.configureEach {
    val taskName = name
    // 拦截所有资源合并任务
    if (taskName.startsWith("merge") && taskName.endsWith("Assets")) {
        // 使用字符串路径 ":FreeToucherServer:buildDex"
        // 这样 Gradle 会在执行阶段再去寻找这个任务，完美避开“找不到任务”的报错
        dependsOn(":FreeToucherServer:buildDex")
    }
}

val cleanServerJar by tasks.registering(Delete::class) {
    group = "build"
    description = "Cleans the generated server.jar from assets folder"

    // 指定要删除的文件路径
    delete(file("src/main/assets/server.jar"))
}

// 将这个任务挂载到标准的 clean 任务上
// 这样当你运行 ./gradlew clean 时，它也会顺便把 server.jar 删掉
tasks.named("clean") {
    dependsOn(cleanServerJar)
}

tasks.configureEach {
    val taskName = name.lowercase()
    if (taskName.contains("lint")) {
        enabled = false
    }
}