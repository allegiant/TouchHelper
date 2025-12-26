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
project(":lib-sdk").projectDir = file("lib-sdk/library")

// === 2. 引入 Android App ===
include(":FreeToucher")
project(":FreeToucher").projectDir = file("FreeToucher/app")

// === 3. 引入 Windows Tool ===
include(":FreeTools")
project(":FreeTools").projectDir = file("FreeTools/composeApp")

// === 4. 引入 Server  ===
include(":FreeToucherServer")
project(":FreeToucherServer").projectDir = file("FreeToucherServer")
