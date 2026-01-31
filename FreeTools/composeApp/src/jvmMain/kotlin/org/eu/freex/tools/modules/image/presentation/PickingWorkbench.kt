package org.eu.freex.tools.modules.image.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.common.model.PickingToolState
import org.eu.freex.tools.modules.image.presentation.features.editor.EditorCanvasPanel
import org.eu.freex.tools.modules.image.presentation.features.editor.layers.FeatureLayer
import org.eu.freex.tools.modules.image.presentation.features.editor.layers.SmartHoverLayer
import org.eu.freex.tools.modules.image.presentation.features.editor.registry.ToolRegistry
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
    val editorState by editorViewModel.uiState.collectAsState()
    var showCodeDialog by remember { mutableStateOf(false) }
    var generatedCode by remember { mutableStateOf("") }

    // === 生命周期管理 ===

    // 退出清理
    DisposableEffect(Unit) {
        onDispose { editorViewModel.setActiveTool(PickingToolState.None) }
    }

    // === 事件处理 ===
    LaunchedEffect(Unit) {
        pickingViewModel.pickEvent.collect { event ->
            // 调用 ToolRegistry，它现在会自动做三件事：PickColor + AddFeaturePoint + Exit
            ToolRegistry.handleEvent(event, editorViewModel)
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
                // 截图
                RailActionButton(
                    icon = Icons.Default.Crop,
                    label = "截图",
                    onClick = { projectListViewModel.captureScreen() }
                )
                Spacer(Modifier.height(16.dp))

                // 导入
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
                Spacer(Modifier.height(16.dp))

                // 复制全部代码 (FeaturePanel 也有复制，这里是全局备份)
                RailActionButton(
                    icon = Icons.Default.ContentCopy,
                    label = "生成脚本",
                    onClick = {
                        generatedCode = mainViewModel.generateScript()
                        showCodeDialog = true
                    }
                )
                Spacer(Modifier.height(16.dp))

                // 取色按钮
                RailActionButton(
                    icon = Icons.Default.Colorize,
                    label = "取色",
                    isActive = editorState.activeTool is PickingToolState.ColorPicker,
                    onClick = {
                        editorViewModel.setActiveTool(PickingToolState.ColorPicker)
                    }
                )
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
                    cursorIcon = if (editorState.activeTool !is PickingToolState.None)
                        PointerIcon(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR))
                    else
                        PointerIcon.Default,

                    content = {
                        if (editorState.displayImage?.image != null) {
                            val image = editorState.displayImage!!.image!!

                            // [加回] 业务图层：显示已添加的特征点 (否则取了色看不到点在哪里)
                            FeatureLayer(
                                viewModel = editorViewModel,
                                sourceImage = image
                            )

                            // 工具图层
                            val renderer = ToolRegistry.getRenderer(editorState.activeTool)
                            renderer.Content(image = image)
                        }
                    },

                    overlay = { size, hoverPos ->
                        if (editorState.displayImage?.image != null) {
                            // 仅当工具激活时显示放大镜
                            val showMagnifier = editorState.activeTool !is PickingToolState.None

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
                        editorViewModel.startCropMode()
                    }
                )
            }
        }

        // --- 弹窗 ---
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
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    NavigationRailItem(
        selected = isActive,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        colors = NavigationRailItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.onSurface,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer
        )
    )
}