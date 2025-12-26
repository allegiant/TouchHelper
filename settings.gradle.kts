rootProject.name = "TouchHelper"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
    // 允许项目覆盖版本目录
    versionCatalogs {
        create("libs") {
            from(files("gradle/libs.versions.toml"))
        }
    }
}

// === 1. 引入 Rust Bridge SDK ===
include(":lib-sdk")
include(":lib-sdk:library") 
project(":lib-sdk:library").projectDir = file("lib-sdk/library")

// === 2. 引入 Android App ===
include(":FreeToucher")
project(":FreeToucher").projectDir = file("FreeToucher/app")

// === 3. 引入 Windows Tool ===
include(":FreeTools")
project("FreeTools").projectDir = file("FreeTools/composeApp")
