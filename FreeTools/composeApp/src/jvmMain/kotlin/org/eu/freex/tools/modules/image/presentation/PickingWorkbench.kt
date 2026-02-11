package org.eu.freex.tools.modules.image.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import java.awt.Cursor
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.eu.freex.tools.common.model.PickEvent
import org.eu.freex.tools.common.model.PickingToolState
import org.eu.freex.tools.common.utils.ImageUtils
import org.eu.freex.tools.common.utils.toHexString
import org.eu.freex.tools.modules.image.presentation.components.PickingToolRail
import org.eu.freex.tools.modules.image.presentation.components.RulerCanvasContainer
import org.eu.freex.tools.modules.image.presentation.components.panel.PickingControlPanel
import org.eu.freex.tools.modules.image.presentation.components.shared.ColorPickingScaffold
import org.eu.freex.tools.modules.image.presentation.components.shared.MultiColorRuleEditor
import org.eu.freex.tools.modules.image.presentation.components.tabs.WorkbenchTabRow
import org.eu.freex.tools.modules.image.presentation.features.editor.EditorCanvasPanel
import org.eu.freex.tools.modules.image.presentation.features.editor.layers.FeatureLayer
import org.eu.freex.tools.modules.image.presentation.features.editor.layers.SmartHoverLayer
import org.eu.freex.tools.modules.image.presentation.features.editor.registry.ToolRegistry
import org.eu.freex.tools.modules.image.presentation.features.tools.dialogs.CodeGenDialog
import org.eu.freex.tools.modules.image.presentation.viewmodel.EditorCanvasViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.PickingToolViewModel
import org.koin.compose.koinInject

@Composable
fun PickingWorkbench(
    pickingViewModel: PickingToolViewModel = koinInject(),
    editorViewModel: EditorCanvasViewModel = koinInject()
) {
    val currentTool by pickingViewModel.currentTool.collectAsState()
    val currentLayer by pickingViewModel.displayImage.collectAsState()
    val featurePoints by pickingViewModel.featurePoints.collectAsState()
    val screenshots by pickingViewModel.screenshots.collectAsState()
    val selectedIndex by pickingViewModel.selectedIndex.collectAsState()

    // MultiColorRuleEditor 状态（来自 VM）
    val multiRules by pickingViewModel.multiColorRules.collectAsState()
    val multiInvert by pickingViewModel.multiColorInvert.collectAsState()
    val multiKeepOriginal by pickingViewModel.multiColorKeepOriginal.collectAsState()

    val safeIndex =
        if (screenshots.isNotEmpty()) {
            selectedIndex.coerceIn(0, screenshots.lastIndex)
        } else {
            0
        }

    var showCodeDialog by remember { mutableStateOf(false) }
    var generatedCode by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    ColorPickingScaffold(
        modifier = Modifier.fillMaxSize(),
        rightPanelWidth = 320.dp,
        leftPanel = {
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
                            runCatching { ImageIO.read(file) }
                                .onSuccess { image ->
                                    if (image != null)
                                        pickingViewModel.addScreenshot(image)
                                }
                                .onFailure { it.printStackTrace() }
                        }
                    }
                }
            )
        },
        centerPanel = {
            Column(modifier = Modifier.fillMaxSize().fillMaxHeight()) {
                WorkbenchTabRow(
                    openedImages = screenshots,
                    selectedIndex = safeIndex,
                    onTabSelected = { index ->
                        pickingViewModel.cancelRuleColorPick()
                        pickingViewModel.selectScreenshot(index)
                    },
                    onTabClosed = { layer ->
                        pickingViewModel.cancelRuleColorPick()
                        val indexToRemove = screenshots.indexOfFirst { it.id == layer.id }
                        if (indexToRemove != -1) {
                            pickingViewModel.closeScreenshot(indexToRemove)
                        }
                    }
                )

                RulerCanvasContainer(
                    modifier = Modifier.weight(1f),
                    viewModel = editorViewModel
                ) {
                    EditorCanvasPanel(
                        modifier = Modifier.fillMaxSize(),
                        displayImage = currentLayer,
                        cursorIcon =
                            PointerIcon(Cursor.getPredefinedCursor(currentTool.cursor)),
                        enablePan = currentTool.enablePan,
                        editorViewModel = editorViewModel,
                        content = {
                            val image = currentLayer?.image
                            if (image != null) {
                                FeatureLayer(points = featurePoints)

                                ToolRegistry.getRenderer(currentTool)
                                    .Content(
                                        image = image,
                                        onEvent = { event ->
                                            when (event) {
                                                is PickEvent.ColorPicked -> {
                                                    val applied =
                                                        pickingViewModel.applyPickedColorToPendingRule(event.color.toHexString())
                                                    if (!applied) {
                                                        pickingViewModel.addPoint(event.x, event.y, event.color)
                                                    }
                                                }

                                                is PickEvent.PointPicked -> {
                                                    val color = ImageUtils.getPixelColor(image, event.x, event.y)
                                                    pickingViewModel.addPoint(event.x, event.y, color)
                                                }

                                                is PickEvent.RegionPicked -> {
                                                    pickingViewModel.cancelRuleColorPick()
                                                    pickingViewModel.setTargetRegion(event.image)
                                                    pickingViewModel.activateTool(PickingToolState.None)
                                                }
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
                                    transformState =
                                        editorViewModel.transformState.value,
                                    hoverPixelPos = hoverPos,
                                    showMagnifier = currentTool.showMagnifier
                                )
                            }
                        }
                    )
                }
            }
        },
        rightPanel = {
            Column(modifier = Modifier.fillMaxSize()) {
                MultiColorRuleEditor(
                    isInvert = multiInvert,
                    keepOriginal = multiKeepOriginal,
                    rules = multiRules,
                    onInvertChange = { pickingViewModel.setMultiColorInvert(it) },
                    onKeepOriginalChange = {
                        pickingViewModel.setMultiColorKeepOriginal(it)
                    },
                    onRulesChange = { pickingViewModel.setMultiColorRules(it) },
                    onRequestPickColor = { index ->
                        pickingViewModel.setPendingPickRuleIndex(index)
                        pickingViewModel.activateTool(PickingToolState.ColorPicker)
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                PickingControlPanel(
                    viewModel = pickingViewModel,
                    onGenerateCode = { code ->
                        generatedCode = code
                        showCodeDialog = true
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            }
        }
    )

    if (showCodeDialog) {
        CodeGenDialog(code = generatedCode, onDismiss = { showCodeDialog = false })
    }
}

private fun pickImageFile(): java.io.File? {
    val fileChooser = JFileChooser()
    fileChooser.dialogTitle = "选择图片"
    fileChooser.fileFilter =
        FileNameExtensionFilter("图片文件 (PNG, JPG, BMP)", "png", "jpg", "jpeg", "bmp")
    val result = fileChooser.showOpenDialog(null)
    return if (result == JFileChooser.APPROVE_OPTION) fileChooser.selectedFile else null
}
