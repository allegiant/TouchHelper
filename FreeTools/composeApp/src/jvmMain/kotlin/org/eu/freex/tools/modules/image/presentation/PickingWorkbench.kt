package org.eu.freex.tools.modules.image.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.common.model.PickingType
import org.eu.freex.tools.modules.image.presentation.features.editor.EditorCanvasPanel
import org.eu.freex.tools.modules.image.presentation.features.editor.layers.ColorPickerLayer
import org.eu.freex.tools.modules.image.presentation.features.editor.layers.FeatureLayer
import org.eu.freex.tools.modules.image.presentation.features.editor.layers.PointPickerLayer
import org.eu.freex.tools.modules.image.presentation.features.editor.layers.SmartHoverLayer
import org.eu.freex.tools.modules.image.presentation.features.feature.FeatureExtractionPanel
import org.eu.freex.tools.modules.image.presentation.features.tools.dialogs.CodeGenDialog
import org.eu.freex.tools.modules.image.presentation.features.tools.dialogs.ScreenCropperDialog
import org.eu.freex.tools.modules.image.presentation.viewmodel.EditorCanvasViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.MainViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.PickingToolViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.ProjectListViewModel
import org.koin.compose.koinInject
import java.awt.Cursor
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
fun PickingWorkbench(
    projectListViewModel: ProjectListViewModel = koinInject(),
    editorViewModel: EditorCanvasViewModel = koinInject(),
    pickingViewModel: PickingToolViewModel = koinInject(),
    mainViewModel: MainViewModel = koinInject()
) {
    var showCodeDialog by remember { mutableStateOf(false) }
    var generatedCode by remember { mutableStateOf("") }

    val editorState by editorViewModel.uiState.collectAsState()

    // === 1. 初始化与生命周期管理 ===
    // 进入时：强制激活工具，设置为取点模式
    LaunchedEffect(Unit) {
        editorViewModel.setPickingType(PickingType.POINT)
        pickingViewModel.setToolActive(true)
        pickingViewModel.setMode(PickingType.POINT)
    }

    // 退出时：关闭工具，重置 Editor 状态
    DisposableEffect(Unit) {
        onDispose {
            pickingViewModel.setToolActive(false)
            editorViewModel.setPickingType(PickingType.NONE)
        }
    }

    // === 2. 核心业务桥接 ===
    // 监听 PickingTool 的事件，并分发给 EditorViewModel
    LaunchedEffect(Unit) {
        pickingViewModel.pickEvent.collect { data ->
            // 无论是取色还是取点，只要在抓抓工具里点击了，就视为添加一个特征点
            // 注意：FeatureExtractionPanel 监听的是 editorViewModel.featurePoints
            editorViewModel.addFeaturePoint(data.x, data.y, data.color)
        }
    }

    // 反向同步：如果 Editor 改变了 pickingType (比如点击列表里的"取色")，工具也要跟进
    LaunchedEffect(editorState.pickingType) {
        if (editorState.pickingType != PickingType.NONE) {
            pickingViewModel.setMode(editorState.pickingType)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {

            // --- 左侧工具栏 ---
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.width(80.dp).fillMaxHeight(),
                header = { Spacer(Modifier.height(8.dp)) }
            ) {
                RailActionButton(
                    icon = Icons.Default.Crop,
                    label = "截图",
                    onClick = { projectListViewModel.captureScreen() }
                )
                Spacer(Modifier.height(16.dp))
                RailActionButton(
                    icon = Icons.Default.AddPhotoAlternate,
                    label = "导入",
                    onClick = {
                        val fileChooser = JFileChooser().apply {
                            fileFilter = FileNameExtensionFilter("Images", "png", "jpg", "bmp", "webp")
                        }
                        if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            projectListViewModel.importImage(fileChooser.selectedFile)
                        }
                    }
                )
                Spacer(Modifier.weight(1f))
                RailActionButton(
                    icon = Icons.Default.ContentCopy,
                    label = "生成脚本",
                    onClick = {
                        generatedCode = mainViewModel.generateScript()
                        showCodeDialog = true
                    }
                )
                Spacer(Modifier.height(16.dp))
            }

            // --- 中间画布 ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color.Black)
            ) {
                val viewportSize = remember { mutableStateOf(IntSize.Zero) }

                EditorCanvasPanel(
                    modifier = Modifier.fillMaxSize(),
                    // 1. 光标由外部控制 (十字光标)
                    cursorIcon = PointerIcon(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)),

                    // 2. 内部层 (跟随缩放)
                    content = {
                        val image = editorState.displayImage!!.image!!

                        // A. 业务图层 (特征点)
                        FeatureLayer(
                            viewModel = editorViewModel,
                            sourceImage = image
                        )

                        // B. 工具交互层 (拦截点击)
                        // 它放在 FeatureLayer 上面，所以点击会先被它捕获
                        // 2. 工具交互层 [拆分后]
                        val mode by pickingViewModel.pickingMode.collectAsState()
                        when (mode) {
                            PickingType.COLOR -> ColorPickerLayer(sourceImage = image)
                            PickingType.POINT -> PointPickerLayer(imageSize = IntSize(image.width, image.height))
                            else -> {}
                        }

                    },

                    // 3. 外部层 (固定悬浮)
                    overlay = { size, hoverPos ->
                        val image = editorState.displayImage!!.image!!

                        SmartHoverLayer(
                            sourceImage = image,
                            containerSize = size,
                            transformState = editorViewModel.transformState.value,
                            hoverPixelPos = hoverPos, // 传入位置
                            showMagnifier = true      // [修改] 抓抓工具：强制显示放大镜
                        )
                    }
                )
            }

            // --- 右侧列表 ---
            Box(
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                FeatureExtractionPanel(
                    modifier = Modifier.fillMaxSize(),
                    viewModel = editorViewModel,
                    onStartRegionSelect = {
                        // 如果需要触发框选，调用 ViewModel 的 startCropMode
                        editorViewModel.startCropMode()
                    }
                )
            }
        }

        // --- 全局弹窗 ---
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
    }
}

@Composable
private fun RailActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    NavigationRailItem(
        selected = false,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
    )
}