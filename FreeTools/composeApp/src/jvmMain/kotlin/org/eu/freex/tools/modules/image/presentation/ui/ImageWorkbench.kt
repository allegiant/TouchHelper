package org.eu.freex.tools.modules.image.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toOffset
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
    val snackbarHostState = remember { SnackbarHostState() }

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
                            val fixedPos = pos?.toOffset() ?: Offset.Zero
                            viewModel.handleEvent(ImageUiEvent.HoverCanvas(fixedPos, color))
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
                onRuleUpdate = { id, rule -> viewModel.handleEvent(ImageUiEvent.UpdateColorRule(id.toLong(), rule)) },
                onRuleToggle = { id, enabled -> viewModel.handleEvent(ImageUiEvent.ToggleColorRule(id.toLong(), enabled)) },
                onRuleRemove = { id -> viewModel.handleEvent(ImageUiEvent.RemoveColorRule(id.toLong())) }
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

        // 【关键修复】Loading 指示器 (移除了背景色)
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(), // 不再设置 background color
                contentAlignment = Alignment.Center
            ) {
                // 为了保证在深色/浅色背景下都可见，可以加一个小圆盘背景，或者直接显示
                // 这里选择加一个小的半透明圆盘，比全屏遮罩体验好得多
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = Color.Black.copy(alpha = 0.5f),
                    elevation = 4.dp
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(8.dp),
                        color = Color.White
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
        )
    }
}