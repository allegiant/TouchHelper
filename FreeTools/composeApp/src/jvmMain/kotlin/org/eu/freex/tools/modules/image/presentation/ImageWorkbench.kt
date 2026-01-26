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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject

// 引入各模块组件
import org.eu.freex.tools.modules.image.presentation.features.editor.EditorCanvasPanel
import org.eu.freex.tools.modules.image.presentation.features.filter.components.InspectorPanel
import org.eu.freex.tools.modules.image.presentation.features.pipeline.ProcessingPipeline
import org.eu.freex.tools.modules.image.presentation.features.project.ProjectListPanel
import org.eu.freex.tools.modules.image.presentation.features.tools.dialogs.ScreenCropperDialog

// 引入 ViewModels
import org.eu.freex.tools.modules.image.presentation.viewmodel.EditorCanvasViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.MainViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.ProjectListViewModel
import org.eu.freex.tools.common.components.LoadingOverlay // 假设你有这个通用组件
import org.eu.freex.tools.common.components.ToastOverlay   // 假设你有这个通用组件
import org.eu.freex.tools.common.model.PickingType
import org.eu.freex.tools.common.model.WorkbenchTab
import org.eu.freex.tools.modules.image.presentation.features.feature.components.drawFeaturePointsOverlay
import org.eu.freex.tools.modules.image.presentation.features.segmentation.components.drawSegmentationOverlay
import org.eu.freex.tools.modules.image.presentation.features.tools.dialogs.CodeGenDialog
import org.eu.freex.tools.modules.image.presentation.viewmodel.SegmentationViewModel
import java.awt.Cursor

@JvmOverloads
@Composable
fun ImageWorkbench(
    mainViewModel: MainViewModel = koinInject(),
    projectListViewModel: ProjectListViewModel = koinInject(),
    editorViewModel: EditorCanvasViewModel = koinInject(),
    segmentationViewModel: SegmentationViewModel = koinInject()
) {

    // 1. 监听全局状态
    val mainState by mainViewModel.uiState.collectAsState()
    val editorState by editorViewModel.uiState.collectAsState()
    val segmentationState by segmentationViewModel.uiState.collectAsState()

    // 状态管理：控制生成代码弹窗的显示与内容
    var showCodeDialog by remember { mutableStateOf(false) }
    var generatedCode by remember { mutableStateOf("") }

    // 2. [关键] 状态提升：管理当前的 Tab (Filter vs Segmentation)
    // 必须放在这里，因为 Canvas 需要知道是否显示切割覆盖层，而 Inspector 需要切换它
    var currentTab by remember { mutableStateOf(WorkbenchTab.FILTER) }
    // [新增] 文本测量器 (用于 Overlay 中绘制文字)
    val textMeasurer = rememberTextMeasurer()

    // [新增] 根据 PickingType 计算光标样式
    val cursorIcon = remember(editorState.pickingType, currentTab) {
        if (editorState.pickingType != PickingType.NONE) {
            // 取点/取色模式：十字准星
            PointerIcon(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR))
        } else if (currentTab == WorkbenchTab.SEGMENTATION) {
            // 切割模式：手型 (暗示可点击)
            PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
        } else {
            // 默认
            PointerIcon.Default
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {

            // --- 左侧：资源列表 ---
            Column(modifier = Modifier.width(260.dp).fillMaxHeight()) {
                Button(
                    onClick = { projectListViewModel.captureScreen() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("屏幕截图")
                }
                Button(
                    onClick = {
                        // 1. 调用 VM 生成代码
                        generatedCode = mainViewModel.generateScript()
                        // 2. 显示弹窗
                        showCodeDialog = true
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("🛠️ 生成运行脚本")
                }
                // ProjectListPanel 内部已注入 VM，无需传参
                ProjectListPanel(modifier = Modifier.weight(1f))
            }

            // --- 中间：画布与流水线 ---
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                // 1. 画布
                EditorCanvasPanel(
                    modifier = Modifier.weight(1f),
                    displayImage = editorState.displayImage,
                    cursorIcon = cursorIcon,
                    showMagnifier = (currentTab == WorkbenchTab.FEATURE) ||
                            (editorState.pickingType != PickingType.NONE), // 取点模式下也强制开启
                    onDrawOverlay = {
                        when (currentTab) {
                            WorkbenchTab.SEGMENTATION -> {
                                drawSegmentationOverlay(
                                    project = segmentationState.project,
                                    textMeasurer = textMeasurer,
                                    selectedIndex = segmentationState.selectedIndex
                                )
                            }
                            WorkbenchTab.FEATURE -> {
                                // [新增] 绘制已添加的特征点
                                drawFeaturePointsOverlay(
                                    points = editorState.featurePoints,
                                    textMeasurer = textMeasurer
                                )
                            }
                            else -> { }
                        }
                    },

                    // [关键修复]
                    onCanvasTap = { event -> // event 是 CanvasTapEvent
                        // 只有在图片范围内点击才有效
                        if (event.isToBounds) {
                            if (editorState.pickingType != PickingType.NONE) {
                                // 处于取色/取点模式，优先处理
                                editorViewModel.onCanvasClick(
                                    Offset(event.pixelPos.x.toFloat(), event.pixelPos.y.toFloat()),
                                    event.color
                                )
                                // 拦截事件，不让下面的 Tab 处理
                                return@EditorCanvasPanel
                            }
                            when (currentTab) {
                                // 1. 滤镜模式 (修复颜色选取)
                                WorkbenchTab.FILTER -> {
                                    // 调用 ViewModel 的通用点击处理 (用于二值化取色等)
                                    // 这里的 Offset 我们传 ScreenPos 还是 PixelPos?
                                    // EditorCanvasViewModel.onCanvasClick 预期的是 Pixel 还是 Screen?
                                    // 看了下源码，它里面用 offset.x.toInt()，如果是像素操作，应该传 PixelPos。
                                    // 我们之前传的是 ScreenPos，这其实是错的，因为 ViewModel 里不知道 scale。
                                    // 所以这里修正为传 PixelPos 对应的 Offset。
                                    editorViewModel.onCanvasClick(
                                        Offset(event.pixelPos.x.toFloat(), event.pixelPos.y.toFloat()),
                                        event.color
                                    )
                                }
                                WorkbenchTab.FEATURE -> {
                                    // [新增] 抓抓模式点击 -> 添加点
                                    editorViewModel.addFeaturePoint(
                                        x = event.pixelPos.x,
                                        y = event.pixelPos.y,
                                        color = event.color
                                    )
                                }
                                // 3. 切割模式
                                WorkbenchTab.SEGMENTATION -> {
                                    segmentationViewModel.onCanvasTap(event.pixelPos.x, event.pixelPos.y)
                                }
                            }
                        }
                    },
                    onCanvasDoubleTap = { event ->
                        if (event.isToBounds && currentTab == WorkbenchTab.SEGMENTATION) {
                            // 1. 先选中该位置的框
                            segmentationViewModel.onCanvasTap(event.pixelPos.x, event.pixelPos.y)
                            // 2. 只有选中了有效框，才弹窗
                            // 我们可以在 ViewModel 里加一个 checkSelectionAndShowDialog，或者简单地直接调
                            segmentationViewModel.showLabelDialog()
                        }
                    }

                )

                // 2. 流水线
                ProcessingPipeline(
                    modifier = Modifier.fillMaxWidth().height(112.dp)
                    // 数据源已在组件内部通过 Koin 注入
                )
            }

            // --- 右侧：属性面板 ---
            InspectorPanel(
                modifier = Modifier.width(320.dp).fillMaxHeight(),
                // 将 Tab 状态下放
                currentTab = currentTab,
                onTabChange = { currentTab = it }
            )
        }

        // --- 全局弹窗层 ---
        // [新增] 代码生成结果弹窗
        if (showCodeDialog) {
            CodeGenDialog(
                code = generatedCode,
                onDismiss = { showCodeDialog = false }
            )
        }

        // 1. 裁剪对话框 (受 EditorViewModel 控制)
        editorState.cropperLayer?.let { layer ->
            ScreenCropperDialog(
                imageLayer = layer,
                onConfirm = { rect -> editorViewModel.confirmCrop(rect) },
                onDismiss = { editorViewModel.exitCropMode() }
            )
        }

        // 2. Loading 遮罩
        if (mainState.isLoading) {
            LoadingOverlay(message = mainState.loadingMessage)
        }

        // 3. Toast 提示
        mainState.toastMessage?.let { msg ->
            ToastOverlay(message = msg, onDismiss = mainViewModel::clearToast)
        }

        // 4. 错误提示 (可选)
        /*
        mainState.errorMessage?.let { error ->
            ErrorDialog(text = error, onDismiss = mainViewModel::clearError)
        }
        */
    }
}