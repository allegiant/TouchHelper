package org.eu.freex.tools

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.eu.freex.tools.common.AppWindowManager
import org.eu.freex.tools.di.appDiModule
import org.eu.freex.tools.modules.image.presentation.features.project.ConfirmScreenCrop
import org.eu.freex.tools.modules.image.presentation.features.tools.DismissDialogs
import org.eu.freex.tools.modules.image.presentation.features.tools.dialogs.ScreenCropperDialog
import org.eu.freex.tools.modules.image.presentation.viewmodel.ImageViewModel
import org.koin.compose.koinInject
import org.koin.core.context.startKoin
import uniffi.touch_core.initDesktop
import java.awt.Dimension

// 【重要】将 main 改为普通函数体，不要直接用 = application
fun main() {
    // 1. 在进入 Compose 之前，先完成全局初始化
    initCore()

    // 2. 启动依赖注入 (全局只执行一次)
    startKoin {
        printLogger()
        modules(appDiModule)
    }

    // 3. 启动 Compose 应用生命周期
    application {
        // --- 这里是 Compose 作用域，可以安全使用 koinInject ---

        // 获取依赖 (这些是在之前的重构中定义的)
        val viewModel = koinInject<ImageViewModel>()
        val windowManager = koinInject<AppWindowManager>()

        // 监听状态
        val imageUiState by viewModel.uiState.collectAsState()
        val isAppVisible by windowManager.isAppVisible.collectAsState()

        // 4. 主应用窗口 (受 WindowManager 控制可见性)
        Window(
            onCloseRequest = ::exitApplication,
            title = "Free Tool Pro (KMP)",
            // 【核心功能】绑定可见性状态，截图时自动隐藏
            visible = isAppVisible,
            state = rememberWindowState(width = 1280.dp, height = 900.dp)
        ) {
            window.minimumSize = Dimension(1000, 700)
            // 注意：根据你的代码，这里传递 window 参数
            App(window = window)
        }

        // 5. 截图遮罩窗口 (独立于主窗口)
        if (imageUiState.cropperImage != null) {
            ScreenCropperDialog(
                image = imageUiState.cropperImage,
                onCropConfirm = { croppedImage ->
                    viewModel.handleEvent(ConfirmScreenCrop(croppedImage))
                },
                onDismiss = {
                    viewModel.handleEvent(DismissDialogs)
                }
            )
        }
    }
}

private fun initCore() {
    try {
        initDesktop()
    } catch (e: Exception) {
        println("Rust Core Init Failed: ${e.message}")
    }
}