package org.eu.freex.tools

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface // M3 Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.eu.freex.tools.modules.image.domain.model.AppModule
// ... 其他 import (ImageUiEvent, ImageWorkbench, Swing 拖拽相关) 保持不变 ...
import org.eu.freex.tools.modules.image.presentation.viewmodel.ImageViewModel // 假设路径正确
import org.eu.freex.tools.modules.image.presentation.ImageWorkbench
import org.eu.freex.tools.modules.image.presentation.features.project.LoadFile
import org.eu.freex.tools.common.theme.AppTheme
import org.eu.freex.tools.common.theme.ThemeMode
import org.koin.compose.koinInject
import java.awt.Component
import java.awt.Container
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.*
import java.io.File

@Composable
fun App(window: androidx.compose.ui.awt.ComposeWindow?) {
    var currentModule by remember { mutableStateOf(AppModule.IMAGE_PROCESSING) }

    // 【新增】主题状态管理
    var themeMode by remember { mutableStateOf(ThemeMode.System) }

    val imageViewModel = koinInject<ImageViewModel>()

    // 拖拽相关代码保持不变...
    if (window != null) {
        DisposableEffect(window) {
            val dropTarget = object : DropTarget() {
                // ... (原有拖拽逻辑代码不变) ...
                override fun drop(evt: DropTargetDropEvent) {
                    try {
                        evt.acceptDrop(DnDConstants.ACTION_COPY)
                        val list = evt.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<*>
                        list.firstOrNull()?.let {
                            val file = it as File
                            val name = file.name.lowercase()
                            if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".bmp") || name.endsWith(".webp")) {
                                imageViewModel.handleEvent(LoadFile(file))
                            }
                        }
                        evt.dropComplete(true)
                    } catch (e: Exception) {
                        e.printStackTrace(); evt.dropComplete(false)
                    }
                }
                // ...
            }
            fun attachToAll(component: Component) {
                component.dropTarget = dropTarget
                if (component is Container) {
                    for (child in component.components) attachToAll(child)
                }
            }
            attachToAll(window)
            onDispose { window.dropTarget = null }
        }
    }

    // 【关键】使用 AppTheme 包裹
    AppTheme(themeMode = themeMode) {
        // 使用 M3 Surface 确保背景色正确应用 (colorScheme.background)
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.fillMaxSize()) {
                TopBar(
                    currentModule = currentModule,
                    themeMode = themeMode,
                    onEvent = imageViewModel::handleEvent,
                    onThemeChange = { themeMode = it },
                    onModuleChange = { currentModule = it }
                )

                Box(Modifier.weight(1f)) {
                    when (currentModule) {
                        AppModule.IMAGE_PROCESSING -> {
                            ImageWorkbench(viewModel = imageViewModel)
                        }
                        AppModule.FONT_MANAGER -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "字库管理模块 - 开发中...",
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}