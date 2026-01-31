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
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.common.model.PickEvent
import org.eu.freex.tools.common.model.PickingToolState
import org.eu.freex.tools.modules.image.presentation.features.editor.EditorCanvasPanel
import org.eu.freex.tools.modules.image.presentation.features.editor.layers.FeatureLayer
import org.eu.freex.tools.modules.image.presentation.features.editor.layers.SmartHoverLayer
import org.eu.freex.tools.modules.image.presentation.features.editor.registry.ToolUIRegistry
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

    // [1] 获取当前工具状态
    val currentTool by pickingViewModel.currentTool.collectAsState()

    // 进入时：默认激活 "取色器"
    LaunchedEffect(Unit) {
        editorViewModel.setActiveTool(PickingToolState.ColorPicker)
    }

    // 状态同步：监听 EditorState 的变化并同步给 ToolVM
    LaunchedEffect(editorState.activeTool) {
        pickingViewModel.activateTool(editorState.activeTool)
    }

    // 退出时：重置为空闲状态
    DisposableEffect(Unit) {
        onDispose {
            editorViewModel.setActiveTool(PickingToolState.None)
        }
    }

    // === 事件处理 (核心逻辑) ===
    LaunchedEffect(Unit) {
        pickingViewModel.pickEvent.collect { event ->
            // [2] 使用智能类型转换处理事件
            when (event) {
                is PickEvent.ColorPicked -> {
                    // 如果是取色，直接添加带颜色的特征点
                    editorViewModel.addFeaturePoint(event.x, event.y, event.color)
                }
                is PickEvent.PointPicked -> {
                    // 如果是取点，添加一个默认颜色(如红色)的特征点
                    editorViewModel.addFeaturePoint(event.x, event.y, Color.Red)
                }
            }
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
                EditorCanvasPanel(
                    modifier = Modifier.fillMaxSize(),
                    // 1. 光标由外部控制 (十字光标)
                    cursorIcon = PointerIcon(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)),

                    // 2. 内部层 (跟随缩放)
                    content = {
                        if (editorState.displayImage?.image != null) {
                            val image = editorState.displayImage!!.image!!
                            // A. 业务图层：显示已添加的特征点
                            FeatureLayer(
                                viewModel = editorViewModel,
                                sourceImage = image
                            )
                            // B. 工具图层：通过注册表加载 (PointPicker 或 ColorPicker)
                            val renderer = ToolUIRegistry.getRenderer(currentTool)
                            renderer.Content(image = image)
                        }
                    },

                    // 3. 外部层 (固定悬浮)
                    overlay = { size, hoverPos ->
                        if (editorState.displayImage?.image != null) {
                            // 在抓抓模式下，只要有工具激活(通常常驻)，就显示放大镜
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