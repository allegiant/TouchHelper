package org.eu.freex.tools

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.di.appDiModule
import org.koin.core.context.startKoin
import uniffi.touch_core.initDesktop

fun main() = application {
    initCore()
    startKoin {
        // 打印 Koin 日志 (可选)
        printLogger()
        // 加载模块
        modules(appDiModule)
    }
    Window(
        onCloseRequest = ::exitApplication,
        title = "Free Tool Pro (KMP)",
        state = rememberWindowState(width = 1280.dp, height = 900.dp)
    ) {
        App(window = window)
    }
}

private fun initCore() {
    try {
        initDesktop()
    } catch (e: Exception) {
        println("Rust Core Init Failed: ${e.message}")
    }
}