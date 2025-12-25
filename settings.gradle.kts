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
}

// === 1. 引入 Rust Bridge SDK ===
include(":lib-sdk")
include(":lib-sdk:library") 
project(":lib-sdk:library").projectDir = file("lib-sdk/library")

// === 2. 引入 Android App ===
include(":android-app")
project(":android-app").projectDir = file("android/app")

// === 3. 引入 Windows Tool ===
include(":freex-tools")
project(":freex-tools").projectDir = file("FreexTools/composeApp")