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
import org.eu.freex.tools.common.model.PickingToolState
import org.eu.freex.tools.common.model.WorkbenchTab
import org.eu.freex.tools.modules.image.presentation.features.editor.EditorCanvasPanel
import org.eu.freex.tools.modules.image.presentation.features.editor.layers.SegmentationLayer
import org.eu.freex.tools.modules.image.presentation.features.editor.layers.SmartHoverLayer
import org.eu.freex.tools.modules.image.presentation.features.editor.registry.ToolUIRegistry
import org.eu.freex.tools.modules.image.presentation.features.filter.components.InspectorPanel
import org.eu.freex.tools.modules.image.presentation.features.pipeline.ProcessingPipeline
import org.eu.freex.tools.modules.image.presentation.features.project.ProjectListPanel
import org.eu.freex.tools.modules.image.presentation.features.tools.dialogs.CodeGenDialog
import org.eu.freex.tools.modules.image.presentation.features.tools.dialogs.ScreenCropperDialog
import org.eu.freex.tools.modules.image.presentation.viewmodel.EditorCanvasViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.MainViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.PickingToolViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.ProjectListViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.SegmentationViewModel
import org.koin.compose.koinInject
import java.awt.Cursor

@Composable
fun ImageWorkbench(
    mainViewModel: MainViewModel = koinInject(),
    projectListViewModel: ProjectListViewModel = koinInject(),
    editorViewModel: EditorCanvasViewModel = koinInject(),
    segmentationViewModel: SegmentationViewModel = koinInject(),
    pickingViewModel: PickingToolViewModel = koinInject()
) {
    val mainState by mainViewModel.uiState.collectAsState()
    val editorState by editorViewModel.uiState.collectAsState()

    // [1] 获取当前工具状态 (Sealed Interface)
    val currentTool by pickingViewModel.currentTool.collectAsState()

    var currentTab by remember { mutableStateOf(WorkbenchTab.FILTER) }
    var showCodeDialog by remember { mutableStateOf(false) }
    var generatedCode by remember { mutableStateOf("") }
    var isSelectingRegion by remember { mutableStateOf(false) }

    // === 状态桥接 [修复后] ===

    // 直接监听 EditorState 的 activeTool 变化，并同步给 PickingViewModel
    LaunchedEffect(editorState.activeTool) {
        // 不需要 when 了，直接传过去！
        pickingViewModel.activateTool(editorState.activeTool)
    }

    // === 2. 监听工具点击事件并分发 [核心修复] ===
    LaunchedEffect(Unit) {
        pickingViewModel.pickEvent.collect { event ->
            when (event) {
                // 情况 A: 取色事件 (event 自动被智能转换为 PickEvent.ColorPicked)
                is PickEvent.ColorPicked -> {
                    editorViewModel.pickColor(event.color)

                    // 退出工具模式 (注意：这里改为调用 setActiveTool 并传入 State 对象)
                    editorViewModel.setActiveTool(PickingToolState.None)
                }

                // 情况 B: 取点事件 (event 自动被智能转换为 PickEvent.PointPicked)
                is PickEvent.PointPicked -> {
                    // [关键修改] 将坐标转发给 EditorViewModel
                    editorViewModel.pickPoint(event.x, event.y)

                    // 打印日志方便调试
                    println("ImageWorkbench 取点: (${event.x}, ${event.y})")

                    // 退出工具模式
                    editorViewModel.setActiveTool(PickingToolState.None)
                }
            }
        }
    }


    // 动态计算光标
    val currentCursor = if (currentTool !is PickingToolState.None) {
        PointerIcon(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR))
    } else {
        PointerIcon.Default
    }

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
                    cursorIcon = currentCursor,
                    // [核心修复区]
                    content = {
                        // 1. 业务层
                        when (currentTab) {
                            WorkbenchTab.SEGMENTATION -> SegmentationLayer(segmentationViewModel)
                            else -> {}
                        }

                        // 2. 工具交互层 [注册表模式实现]
                        if (editorState.displayImage?.image != null) {
                            val image = editorState.displayImage!!.image!!
                            // A. 查表
                            val renderer = ToolUIRegistry.getRenderer(currentTool)
                            // B. 渲染
                            renderer.Content(image = image)
                        }
                    },
                    overlay = { size, hoverPos ->
                        if (editorState.displayImage?.image != null) {
                            // 只要不是 None，就显示放大镜
                            val showMagnifier = currentTool !is PickingToolState.None
                            SmartHoverLayer(
                                sourceImage = editorState.displayImage!!.image!!,
                                containerSize = size,
                                transformState = editorViewModel.transformState.value,
                                hoverPixelPos = hoverPos,
                                showMagnifier = showMagnifier
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
                onStartRegionSelect = { isSelectingRegion = true }
            )
        }

        // ... 全局弹窗保持不变 ...
        if (showCodeDialog) {
            CodeGenDialog(code = generatedCode, onDismiss = { showCodeDialog = false })
        }
        editorState.cropperLayer?.let { layer ->
            ScreenCropperDialog(
                imageLayer = layer,
                onConfirm = { rect -> editorViewModel.confirmCrop(rect) },
                onDismiss = { editorViewModel.exitCropMode() }
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