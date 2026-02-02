package org.eu.freex.tools.modules.image.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerIcon
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
import org.eu.freex.tools.modules.image.presentation.viewmodel.MainViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.PickingToolViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.ProjectListViewModel
import org.koin.compose.koinInject
import java.awt.Cursor

@Composable
fun PickingWorkbench(
    projectListViewModel: ProjectListViewModel = koinInject(),
    editorViewModel: EditorCanvasViewModel = koinInject(),
    pickingViewModel: PickingToolViewModel = koinInject(),
    mainViewModel: MainViewModel = koinInject()
) {
    val editorState by editorViewModel.uiState.collectAsState()
    val projectListState by projectListViewModel.uiState.collectAsState()
    val pickingState by pickingViewModel.currentTool.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {

        // 1. 顶部：多文件标签页
        WorkbenchTabRow(
            openedImages = projectListState.assets, // 假设 ProjectListViewModel 有这个列表
            selectedIndex = 0, // 暂时写死，后续绑定到 ViewModel
            onTabSelected = {},
            onTabClosed = {}
        )

        // 2. 主工作区
        Row(modifier = Modifier.weight(1f)) {

            // 2.1 左侧：工具条
            PickingToolRail(
                activeTool = pickingState,
                onToolSelect = { pickingViewModel.activateTool(it) },
                onCapture = { projectListViewModel.captureScreen() }
            )

            // 2.2 中间：带标尺画布
            RulerCanvasContainer(
                modifier = Modifier.weight(1f)
            ) {
                println("光标: ${editorState.activeTool.cursor}")
                // 复用核心画布
                EditorCanvasPanel(
                    modifier = Modifier.fillMaxSize(),
                    cursorIcon =  PointerIcon(Cursor.getPredefinedCursor(editorState.activeTool.cursor)),
                    enablePan = pickingState.enablePan,
                    content = {
                        if (editorState.displayImage?.image != null) {
                            val image = editorState.displayImage!!.image!!
                            // 业务图层：显示点
                            FeatureLayer(editorViewModel, image)
                            // 工具图层：显示框选/准星
                            ToolRegistry.getRenderer(pickingState).Content(image)
                        }
                    },

                    overlay = { size, hoverPos ->
                        val image = editorState.displayImage?.image

                        // 任务 A: 恢复画布上的跟随放大镜
                        if (image != null) {
                            SmartHoverLayer(
                                sourceImage = image,
                                containerSize = size,
                                transformState = editorViewModel.transformState.value,
                                hoverPixelPos = hoverPos,
                                showMagnifier = editorState.activeTool.showMagnifier
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
                onGenerateCode = { /* TODO */ }
            )
        }
    }
}