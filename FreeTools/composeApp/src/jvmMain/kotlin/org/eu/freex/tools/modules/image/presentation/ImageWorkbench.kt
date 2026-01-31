/* Path: composeApp/src/jvmMain/kotlin/org/eu/freex/tools/modules/image/presentation/ImageWorkbench.kt */
package org.eu.freex.tools.modules.image.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.unit.IntSize // [新增导入]
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import org.eu.freex.tools.common.components.LoadingOverlay
import org.eu.freex.tools.common.components.ToastOverlay
import org.eu.freex.tools.common.model.PickingType
import org.eu.freex.tools.common.model.WorkbenchTab
import org.eu.freex.tools.modules.image.presentation.features.editor.EditorCanvasPanel
import org.eu.freex.tools.modules.image.presentation.features.editor.layers.ColorPickerLayer
import org.eu.freex.tools.modules.image.presentation.features.editor.layers.PointPickerLayer
import org.eu.freex.tools.modules.image.presentation.features.editor.layers.SegmentationLayer
import org.eu.freex.tools.modules.image.presentation.features.editor.layers.SmartHoverLayer
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
    val isToolActive by pickingViewModel.isActive.collectAsState()

    var currentTab by remember { mutableStateOf(WorkbenchTab.FILTER) }
    var showCodeDialog by remember { mutableStateOf(false) }
    var generatedCode by remember { mutableStateOf("") }
    var isSelectingRegion by remember { mutableStateOf(false) }

    // === 1. 监听来自属性面板的取色请求 ===
    LaunchedEffect(editorState.pickingType) {
        if (editorState.pickingType != PickingType.NONE) {
            pickingViewModel.setMode(editorState.pickingType)
            pickingViewModel.setToolActive(true)
        } else {
            pickingViewModel.setToolActive(false)
        }
    }

    // === 2. 监听工具点击事件并分发 [核心修复] ===
    LaunchedEffect(Unit) {
        pickingViewModel.pickEvent.collect { data ->
            when (data.type) {
                // 情况 A: 取色
                PickingType.COLOR -> {
                    editorViewModel.pickColor(data.color)
                    editorViewModel.setPickingType(PickingType.NONE)
                }

                // 情况 B: 取点 (修复此处)
                PickingType.POINT -> {
                    // [关键修改]
                    // 不要调用 segmentationViewModel.onCanvasTap (那是用来选框的)
                    // 要调用 editorViewModel.pickPoint (这是用来回填坐标给属性面板的)
                    editorViewModel.pickPoint(data.x, data.y)

                    // 打印日志方便调试
                    println("ImageWorkbench 取点: (${data.x}, ${data.y})")

                    // 退出取点模式
                    editorViewModel.setPickingType(PickingType.NONE)
                }

                else -> {}
            }
        }
    }

    val currentCursor = if (isToolActive) {
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

                        // 2. 工具交互层 (必须存在，否则放大镜模式下点击无效)
                        // 注意：因为它盖在 SegmentationLayer 上面，所以它会拦截点击事件。
                        // 这就是为什么我们需要上面的 "pickEvent.collect" 来手动把坐标传回给 segmentationViewModel。
                        if (isToolActive && editorState.displayImage?.image != null) {
                            val image = editorState.displayImage!!.image!!
                            // [修改点] 根据当前的 PickingType 决定加载哪个 Layer
                            when (editorState.pickingType) {
                                PickingType.COLOR -> {
                                    ColorPickerLayer(sourceImage = image)
                                }
                                PickingType.POINT -> {
                                    // 取点只需要尺寸
                                    PointPickerLayer(
                                        imageSize = IntSize(image.width, image.height)
                                    )
                                }
                                else -> {
                                    // 如果有其他模式，或者为了兼容，可以保留老的 PickingToolLayer
                                    // 但既然拆分了，建议这里只处理这两个
                                }
                            }
                        }
                    },
                    overlay = { size, hoverPos ->
                        if (editorState.displayImage?.image != null) {
                            SmartHoverLayer(
                                sourceImage = editorState.displayImage!!.image!!,
                                containerSize = size,
                                transformState = editorViewModel.transformState.value,
                                hoverPixelPos = hoverPos,
                                showMagnifier = isToolActive
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