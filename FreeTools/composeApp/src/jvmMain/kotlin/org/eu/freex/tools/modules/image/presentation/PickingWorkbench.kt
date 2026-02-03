package org.eu.freex.tools.modules.image.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.common.model.PickEvent
import org.eu.freex.tools.common.model.PickingToolState
import org.eu.freex.tools.modules.image.presentation.components.PickingToolRail
import org.eu.freex.tools.modules.image.presentation.components.RulerCanvasContainer
import org.eu.freex.tools.modules.image.presentation.components.panel.PickingControlPanel
import org.eu.freex.tools.modules.image.presentation.components.tabs.WorkbenchTabRow
import org.eu.freex.tools.modules.image.presentation.features.editor.EditorCanvasPanel
import org.eu.freex.tools.modules.image.presentation.features.editor.layers.FeatureLayer
import org.eu.freex.tools.modules.image.presentation.features.editor.layers.SmartHoverLayer
import org.eu.freex.tools.modules.image.presentation.features.editor.registry.ToolRegistry
import org.eu.freex.tools.modules.image.presentation.viewmodel.EditorCanvasViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.PickingToolViewModel
import org.koin.compose.koinInject
import java.awt.Cursor

@Composable
fun PickingWorkbench(
    pickingViewModel: PickingToolViewModel = koinInject(),
    editorViewModel: EditorCanvasViewModel = koinInject()
) {
    val activeTool by pickingViewModel.currentTool.collectAsState()

    val currentTool by pickingViewModel.currentTool.collectAsState()
    val currentLayer by pickingViewModel.displayImage.collectAsState()
    val featurePoints by pickingViewModel.featurePoints.collectAsState()

    val screenshots by pickingViewModel.screenshots.collectAsState()
    val selectedIndex by pickingViewModel.selectedIndex.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {

        // 1. 顶部：多文件标签页
        WorkbenchTabRow(
            openedImages = screenshots,
            selectedIndex = selectedIndex,
            onTabSelected = { index -> pickingViewModel.selectScreenshot(index) },
            onTabClosed = { index -> pickingViewModel.closeScreenshot(index) }
        )

        // 2. 主工作区
        Row(modifier = Modifier.weight(1f)) {
            // 2.1 左侧：工具条
            PickingToolRail(
                activeTool = activeTool,
                onToolSelect = { pickingViewModel.activateTool(it) },
                onCapture = {  }
            )

            // 2.2 中间：带标尺画布
            RulerCanvasContainer(
                modifier = Modifier.weight(1f)
            ) {
                // 复用核心画布
                EditorCanvasPanel(
                    modifier = Modifier.fillMaxSize(),
                    displayImage = currentLayer,
                    cursorIcon = PointerIcon(Cursor.getPredefinedCursor(activeTool.cursor)),
                    enablePan = activeTool.enablePan,
                    content = {
                        if (currentLayer?.image != null) {
                            val image = currentLayer!!.image!!
                            // 业务图层：显示点
                            FeatureLayer(pickingViewModel, image)
                            // 工具图层：显示框选/准星
                            ToolRegistry.getRenderer(activeTool).Content(
                                image = image,
                                onEvent = { event ->
                                    when (event) {
                                        is PickEvent.RegionPicked -> {
                                            // 收到图片，传给 VM 设置为二值化目标
                                            pickingViewModel.setTargetRegion(event.image)
                                            // 可选：裁剪完后自动切回普通模式
                                            pickingViewModel.activateTool(PickingToolState.None)
                                        }
                                        else -> {}
                                    }
                                }
                            )
                        }
                    },

                    overlay = { size, hoverPos ->
                        val image = pickingViewModel.displayImage.value

                        // 任务 A: 恢复画布上的跟随放大镜
                        if (image != null) {
                            SmartHoverLayer(
                                sourceImage = image.image!!,
                                containerSize = size,
                                transformState = editorViewModel.transformState.value,
                                hoverPixelPos = hoverPos,
                                showMagnifier = activeTool.showMagnifier
                            )
                        }

                        // 任务 B: 处理框选 (模拟)
                        // 实际上你需要一个“框选工具” (PickingToolState.RegionPicker)
                        // 当用户拖拽结束时，EditorCanvasPanel 会有一个回调或者 State 变化。
                        // 假设 EditorViewModel 有一个 selectionRect 状态：

                        /* 伪代码集成逻辑：
                           if (editorState.isSelectionFinished && editorState.selectionRect != null) {
                               val rect = editorState.selectionRect
                               val crop = image.getSubimage(rect.x, rect.y, rect.width, rect.height)
                               pickingViewModel.setTargetRegion(crop)
                               editorViewModel.clearSelectionSignal() // 消费掉这个事件
                           }
                        */
                    }
                )
            }

            // 2.3 右侧：控制面板
            PickingControlPanel(
                viewModel = pickingViewModel,
                onGenerateCode = { /* TODO */ },
                modifier = Modifier.width(320.dp)
            )
        }
    }
}