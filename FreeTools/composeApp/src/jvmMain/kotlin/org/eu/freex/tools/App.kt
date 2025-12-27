package org.eu.freex.tools

// 【关键变化】引入新的 Feature 层组件
// 引入旧的 TopBar (假设您还没重构它)
// 拖拽相关
// 引入新的 Event 用于拖拽加载
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.eu.freex.tools.modules.image.presentation.contract.ImageUiEvent
import org.eu.freex.tools.modules.image.presentation.ui.ImageWorkbench
import org.eu.freex.tools.modules.image.presentation.viewmodel.ImageViewModel
import java.awt.Component
import java.awt.Container
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.io.File

@Composable
fun App(window: androidx.compose.ui.awt.ComposeWindow?) {
    var currentModule by remember { mutableStateOf(AppModule.IMAGE_PROCESSING) }

    // 1. 创建新的 ViewModel (MVI 架构)
    val imageViewModel = remember { ImageViewModel() }

    // 2. 配置窗口拖拽支持 (直接发送 Event 到 ViewModel)
    if (window != null) {
        DisposableEffect(window) {
            val dropTarget = object : DropTarget() {
                override fun dragEnter(dtde: DropTargetDragEvent) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY)
                }

                override fun dragOver(dtde: DropTargetDragEvent) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY)
                }

                override fun drop(evt: DropTargetDropEvent) {
                    try {
                        evt.acceptDrop(DnDConstants.ACTION_COPY)
                        val list =
                            evt.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<*>
                        list.firstOrNull()?.let {
                            val file = it as File
                            val name = file.name.lowercase()
                            if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".bmp") || name.endsWith(
                                    ".webp"
                                )
                            ) {
                                // 【关键】通过 handleEvent 分发加载事件
                                imageViewModel.handleEvent(ImageUiEvent.LoadFile(file))
                            }
                        }
                        evt.dropComplete(true)
                    } catch (e: Exception) {
                        e.printStackTrace(); evt.dropComplete(false)
                    }
                }
            }

            // 递归绑定拖拽监听
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

    // 3. 主界面布局
    Column(Modifier.fillMaxSize()) {
        TopBar(currentModule = currentModule, onModuleChange = { currentModule = it })

        Box(Modifier.weight(1f)) {
            when (currentModule) {
                AppModule.IMAGE_PROCESSING -> {
                    // 【关键变化】使用新的 Workbench，并传入 ViewModel
                    ImageWorkbench(viewModel = imageViewModel)
                }

                AppModule.FONT_MANAGER -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("字库管理模块 - 开发中...", color = Color.Gray)
                    }
                }
            }
        }
    }
}