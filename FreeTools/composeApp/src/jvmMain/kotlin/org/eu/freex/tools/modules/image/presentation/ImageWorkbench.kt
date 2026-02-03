package org.eu.freex.tools.modules.image.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.common.components.LoadingOverlay
import org.eu.freex.tools.common.components.ToastOverlay
import org.eu.freex.tools.common.model.PickEvent
import org.eu.freex.tools.common.model.WorkbenchTab
import org.eu.freex.tools.modules.image.presentation.features.editor.EditorCanvasPanel
import org.eu.freex.tools.modules.image.presentation.features.editor.layers.SegmentationLayer
import org.eu.freex.tools.modules.image.presentation.features.editor.layers.SmartHoverLayer
import org.eu.freex.tools.modules.image.presentation.features.editor.registry.ToolRegistry
import org.eu.freex.tools.modules.image.presentation.features.filter.components.InspectorPanel
import org.eu.freex.tools.modules.image.presentation.features.pipeline.ProcessingPipeline
import org.eu.freex.tools.modules.image.presentation.features.project.ProjectListPanel
import org.eu.freex.tools.modules.image.presentation.features.tools.dialogs.CodeGenDialog
import org.eu.freex.tools.modules.image.presentation.features.tools.dialogs.ScreenCropperDialog
import org.eu.freex.tools.modules.image.presentation.viewmodel.EditorCanvasViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.ImageWorkbenchViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.MainViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.ProjectListViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.SegmentationViewModel
import org.koin.compose.koinInject
import java.awt.Cursor

@Composable
fun ImageWorkbench(
    mainViewModel: MainViewModel = koinInject(),
    projectListViewModel: ProjectListViewModel = koinInject(),
    workbenchViewModel: ImageWorkbenchViewModel = koinInject(),
    editorViewModel: EditorCanvasViewModel = koinInject(),
    segmentationViewModel: SegmentationViewModel = koinInject(),
) {


    val mainState by mainViewModel.uiState.collectAsState()
    val workbenchState by workbenchViewModel.uiState.collectAsState()

    var currentTab by remember { mutableStateOf(WorkbenchTab.FILTER) }
    var showCodeDialog by remember { mutableStateOf(false) }
    var generatedCode by remember { mutableStateOf("") }


    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            // 左侧资源列表
            Column(modifier = Modifier.width(260.dp).fillMaxHeight()) {
                Button(
                    onClick = { projectListViewModel.captureScreen() },
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp)
                ) {
                    Text("屏幕截图")
                }
                Button(
                    onClick = {
                        generatedCode = mainViewModel.generateScript()
                        showCodeDialog = true
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("🛠️ 生成运行脚本")
                }
                ProjectListPanel(modifier = Modifier.weight(1f))
            }

            // 中间画布
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                EditorCanvasPanel(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    displayImage = workbenchState.displayImage,
                    cursorIcon = PointerIcon(Cursor.getPredefinedCursor(workbenchState.activeTool.cursor)),
                    // [核心修复区]
                    content = {
                        // 1. 业务层
                        when (currentTab) {
                            WorkbenchTab.SEGMENTATION -> SegmentationLayer(segmentationViewModel)
                            else -> {}
                        }

                        // 2. 工具交互层 [注册表模式实现]
                        if (workbenchState.displayImage?.image != null) {
                            val image = workbenchState.displayImage!!.image!!
                            // 直接用 Editor 的状态查表！

                            ToolRegistry.getRenderer(workbenchState.activeTool).Content(
                                image = image,
                                onEvent = { event ->
                                    // === 事件分发中心 ===
                                    // 这里决定了“在主编辑器里点击”会发生什么
                                    when (event) {
                                        // 场景 1: 取色 (例如用于设置滤镜参数)
                                        is PickEvent.ColorPicked -> {
                                            workbenchViewModel.pickColor(event.color)
                                        }
                                        // 场景 3: 取点 (例如特征匹配)
                                        is PickEvent.PointPicked -> {
                                            workbenchViewModel.pickPoint(event.x, event.y)
                                        }
                                        else -> {println("Unknown event: $event")}
                                    }
                                }
                            )
                        }
                    },
                    overlay = { size, hoverPos ->
                        if (workbenchState.displayImage?.image != null) {
                            SmartHoverLayer(
                                sourceImage = workbenchState.displayImage!!.image!!,
                                containerSize = size,
                                transformState = editorViewModel.transformState.value,
                                hoverPixelPos = hoverPos,
                                showMagnifier = workbenchState.activeTool.showMagnifier
                            )
                        }
                    }
                )
                ProcessingPipeline(modifier = Modifier.fillMaxWidth().height(112.dp))
            }

            // 右侧属性面板
            InspectorPanel(
                modifier = Modifier.width(320.dp).fillMaxHeight(),
                currentTab = currentTab,
                onTabChange = { currentTab = it },
            )
        }

        // ... 全局弹窗保持不变 ...
        if (showCodeDialog) {
            CodeGenDialog(code = generatedCode, onDismiss = { showCodeDialog = false })
        }
        workbenchState.cropperLayer?.let { layer ->
            ScreenCropperDialog(
                imageLayer = layer,
                onConfirm = { rect -> workbenchViewModel.confirmCrop(rect) },
                onDismiss = { workbenchViewModel.exitCropMode() }
            )
        }
        if (mainState.isLoading) {
            LoadingOverlay(message = mainState.loadingMessage)
        }
        mainState.toastMessage?.let { msg ->
            ToastOverlay(message = msg, onDismiss = mainViewModel::clearToast)
        }
    }
}