package org.eu.freex.tools.modules.image.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
import org.eu.freex.tools.modules.image.presentation.features.tools.dialogs.CodeGenDialog
import org.eu.freex.tools.modules.image.presentation.viewmodel.EditorCanvasViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.PickingToolViewModel
import org.koin.compose.koinInject
import java.awt.Cursor
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
fun PickingWorkbench(
    pickingViewModel: PickingToolViewModel = koinInject(),
    editorViewModel: EditorCanvasViewModel = koinInject()
) {
    // === 状态收集 ===
    val currentTool by pickingViewModel.currentTool.collectAsState()
    val currentLayer by pickingViewModel.displayImage.collectAsState()
    val featurePoints by pickingViewModel.featurePoints.collectAsState()
    val screenshots by pickingViewModel.screenshots.collectAsState()
    val selectedIndex by pickingViewModel.selectedIndex.collectAsState()

    // === 状态修复 ===
    // [关键修复1] 计算安全索引，防止 "Index 1 out of bounds" 崩溃
    // 如果 selectedIndex 还没来得及更新，我们强制把它限制在 screenshots 有效范围内
    val safeIndex = if (screenshots.isNotEmpty()) {
        selectedIndex.coerceIn(0, screenshots.lastIndex)
    } else {
        0
    }

    // 弹窗状态
    var showCodeDialog by remember { mutableStateOf(false) }
    var generatedCode by remember { mutableStateOf("") }

    // 协程作用域 (用于文件导入)
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {

        // 1. 顶部：多文件标签页
        WorkbenchTabRow(
            openedImages = screenshots,
            selectedIndex = safeIndex, // [使用修复后的 safeIndex]
            onTabSelected = { index -> pickingViewModel.selectScreenshot(index) },
            // [关键修复2] 解决 "Type Mismatch" 报错
            // TabRow 传出来的是 ImageLayer 对象，我们在这里手动转成 index 再传给 ViewModel
            onTabClosed = { layer ->
                val indexToRemove = screenshots.indexOfFirst { it.id == layer.id }
                if (indexToRemove != -1) {
                    pickingViewModel.closeScreenshot(indexToRemove)
                }
            }
        )

        // 2. 主工作区
        Row(modifier = Modifier.weight(1f)) {
            // 2.1 左侧：工具条
            PickingToolRail(
                activeTool = currentTool,
                onToolSelect = { pickingViewModel.activateTool(it) },
                onCapture = {
                    // TODO: 调用你的 ScreenCaptureService
                },
                onImport = {
                    scope.launch(Dispatchers.IO) {
                        val file = pickImageFile()
                        if (file != null) {
                            try {
                                val image = ImageIO.read(file)
                                if (image != null) {
                                    pickingViewModel.addScreenshot(image)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            )

            // 2.2 中间：带标尺画布
            RulerCanvasContainer(
                modifier = Modifier.weight(1f)
            ) {
                EditorCanvasPanel(
                    modifier = Modifier.fillMaxSize(),
                    displayImage = currentLayer,
                    cursorIcon = PointerIcon(Cursor.getPredefinedCursor(currentTool.cursor)),
                    enablePan = currentTool.enablePan,
                    editorViewModel = editorViewModel,
                    content = {
                        val image = currentLayer?.image
                        if (image != null) {
                            // A. 业务图层 (显示已添加的点)
                            // [注意] 确保 FeatureLayer 已经改为只接收 List<FeaturePoint>
                            FeatureLayer(points = featurePoints)

                            // B. 工具图层 (响应点击/框选)
                            ToolRegistry.getRenderer(currentTool).Content(
                                image = image,
                                onEvent = { event ->
                                    when (event) {
                                        is PickEvent.ColorPicked -> {
                                            pickingViewModel.addPoint(event.x, event.y, event.color)
                                        }
                                        is PickEvent.RegionPicked -> {
                                            pickingViewModel.setTargetRegion(event.image)
                                            pickingViewModel.activateTool(PickingToolState.None)
                                        }
                                        else -> {}
                                    }
                                }
                            )
                        }
                    },
                    overlay = { size, hoverPos ->
                        val image = currentLayer?.image
                        if (image != null) {
                            SmartHoverLayer(
                                sourceImage = image,
                                containerSize = size,
                                transformState = editorViewModel.transformState.value,
                                hoverPixelPos = hoverPos,
                                showMagnifier = currentTool.showMagnifier
                            )
                        }
                    }
                )
            }

            // 2.3 右侧：控制面板
            PickingControlPanel(
                viewModel = pickingViewModel,
                onGenerateCode = { code ->
                    generatedCode = code
                    showCodeDialog = true
                },
                modifier = Modifier.width(320.dp)
            )
        }
    }

    // 代码生成弹窗
    if (showCodeDialog) {
        CodeGenDialog(
            code = generatedCode,
            onDismiss = { showCodeDialog = false }
        )
    }
}

/**
 * 辅助函数：打开文件选择器
 */
private fun pickImageFile(): java.io.File? {
    val fileChooser = JFileChooser()
    fileChooser.dialogTitle = "选择图片"
    fileChooser.fileFilter = FileNameExtensionFilter("图片文件 (PNG, JPG, BMP)", "png", "jpg", "jpeg", "bmp")
    val result = fileChooser.showOpenDialog(null)
    return if (result == JFileChooser.APPROVE_OPTION) fileChooser.selectedFile else null
}