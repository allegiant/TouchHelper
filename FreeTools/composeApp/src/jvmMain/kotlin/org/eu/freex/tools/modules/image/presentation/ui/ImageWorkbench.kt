package org.eu.freex.tools.modules.image.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.dialogs.CharMappingDialog
import org.eu.freex.tools.dialogs.ScreenCropperDialog
import org.eu.freex.tools.modules.image.presentation.contract.ImageUiEvent
import org.eu.freex.tools.modules.image.presentation.ui.components.EditorCanvas
import org.eu.freex.tools.modules.image.presentation.ui.components.InspectorPanel
import org.eu.freex.tools.modules.image.presentation.ui.components.ProcessingPipeline
import org.eu.freex.tools.modules.image.presentation.ui.components.ProjectExplorer
import org.eu.freex.tools.modules.image.presentation.viewmodel.ImageViewModel
import org.eu.freex.tools.utils.ImageUtils

@Composable
fun ImageWorkbench(
    viewModel: ImageViewModel = remember { ImageViewModel() }
) {
    val state by viewModel.uiState.collectAsState()

    // 1. Snackbar 状态
    val snackbarHostState = remember { SnackbarHostState() }

    // 2. 监听 ViewModel 的副作用 (Toast/Error 消息)
    LaunchedEffect(viewModel) {
        viewModel.uiEffect.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF252526))) {
        Row(modifier = Modifier.fillMaxSize()) {

            // --- 左侧 ---
            ProjectExplorer(
                modifier = Modifier.width(260.dp).fillMaxHeight(),
                sourceImages = state.sourceImages,
                selectedIndex = state.selectedSourceIndex,
                onSelect = { viewModel.handleEvent(ImageUiEvent.SelectSourceImage(it)) },
                onImportFile = { file -> viewModel.handleEvent(ImageUiEvent.LoadFile(file)) },
                onScreenCapture = { viewModel.handleEvent(ImageUiEvent.StartScreenCapture) },
                onRemove = { viewModel.handleEvent(ImageUiEvent.RemoveSourceImage(it)) }
            )

            // --- 中间 ---
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFF1E1E1E))) {
                    EditorCanvas(
                        modifier = Modifier.fillMaxSize(),
                        workImage = state.activeDisplayImage,
                        binaryPreview = state.binaryPreview,
                        scale = state.mainScale,
                        offset = state.mainOffset,
                        hoverColor = state.hoverColor,
                        hoverPos = state.hoverPixelPos,
                        onTransformChange = { s, o ->
                            viewModel.handleEvent(ImageUiEvent.UpdateCanvasTransform(s, o))
                        },
                        onHover = { pos, color ->
                            viewModel.handleEvent(ImageUiEvent.HoverCanvas(pos, color))
                        },
                        onColorPick = { hex ->
                            viewModel.handleEvent(ImageUiEvent.ColorPick(hex))
                        }
                    )
                }
                ProcessingPipeline(
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    processChain = state.displayChain,
                    selectedIndex = state.selectedPipelineIndex,
                    onSelect = { viewModel.handleEvent(ImageUiEvent.SelectPipelineStep(it)) },
                    onDelete = { viewModel.handleEvent(ImageUiEvent.DeletePipelineStep(it)) }
                )
            }

            // --- 右侧 ---
            InspectorPanel(
                modifier = Modifier.width(320.dp).fillMaxHeight(),
                selectedTab = state.rightPanelTabIndex,
                currentFilter = state.currentFilter,
                thresholdRange = state.thresholdRange,
                isRgbAvgEnabled = state.isRgbAvgEnabled,
                colorRules = state.activeColorRules,

                onTabChange = { viewModel.handleEvent(ImageUiEvent.ChangePanelTab(it)) },
                onFilterChange = { viewModel.handleEvent(ImageUiEvent.SelectFilter(it)) },
                onThresholdChange = { viewModel.handleEvent(ImageUiEvent.UpdateThreshold(it)) },
                onRgbAvgChange = { viewModel.handleEvent(ImageUiEvent.ToggleRgbAvg(it)) },
                onAddStep = { viewModel.handleEvent(ImageUiEvent.ApplyCurrentFilter) },
                onModifyStep = { viewModel.handleEvent(ImageUiEvent.ModifyCurrentStep) },
                onRuleUpdate = { id, bias -> viewModel.handleEvent(ImageUiEvent.UpdateColorRule(id, bias)) },
                onRuleToggle = { id, enabled -> viewModel.handleEvent(ImageUiEvent.ToggleColorRule(id, enabled)) },
                onRuleRemove = { id -> viewModel.handleEvent(ImageUiEvent.RemoveColorRule(id)) }
            )
        }

        // --- 全局弹窗层 ---
        if (state.isScreenCropperVisible && state.fullScreenCapture != null) {
            ScreenCropperDialog(
                fullScreenImage = state.fullScreenCapture!!,
                onDismiss = { viewModel.handleEvent(ImageUiEvent.DismissDialogs) },
                onCropConfirm = { cropped -> viewModel.handleEvent(ImageUiEvent.ConfirmScreenCrop(cropped)) }
            )
        }

        if (state.isMappingDialogVisible && state.mappingBitmap != null) {
            CharMappingDialog(
                bitmap = state.mappingBitmap!!.toComposeImageBitmap(),
                onDismiss = { viewModel.handleEvent(ImageUiEvent.DismissDialogs) },
                onConfirm = { char -> viewModel.handleEvent(ImageUiEvent.ConfirmMapping(char)) }
            )
        }

        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }

        // 3. 全局 SnackbarHost (显示在最上层底部)
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
        )
    }
}