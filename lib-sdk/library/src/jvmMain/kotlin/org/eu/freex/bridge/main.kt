package org.eu.freex.bridge

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "lib-sdk",
    ) {
        App()
    }
}