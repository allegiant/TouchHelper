package org.eu.freex.tools.modules.image.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toOffset
import org.eu.freex.tools.modules.image.presentation.ui.dialogs.CharMappingDialog
import org.eu.freex.tools.modules.image.presentation.ui.dialogs.ScreenCropperDialog
import org.eu.freex.tools.modules.image.presentation.contract.events.ChangePanelTab
import org.eu.freex.tools.modules.image.presentation.contract.events.ColorPick
import org.eu.freex.tools.modules.image.presentation.contract.events.ConfirmMapping
import org.eu.freex.tools.modules.image.presentation.contract.events.ConfirmScreenCrop
import org.eu.freex.tools.modules.image.presentation.contract.events.DeletePipelineStep
import org.eu.freex.tools.modules.image.presentation.contract.events.DismissDialogs
import org.eu.freex.tools.modules.image.presentation.contract.events.HoverCanvas
import org.eu.freex.tools.modules.image.presentation.contract.events.LoadFile
import org.eu.freex.tools.modules.image.presentation.contract.events.RemoveSourceImage
import org.eu.freex.tools.modules.image.presentation.contract.events.SelectPipelineStep
import org.eu.freex.tools.modules.image.presentation.contract.events.SelectSourceImage
import org.eu.freex.tools.modules.image.presentation.contract.events.StartScreenCapture
import org.eu.freex.tools.modules.image.presentation.contract.events.UpdateCanvasTransform
import org.eu.freex.tools.modules.image.presentation.ui.components.EditorCanvas
import org.eu.freex.tools.modules.image.presentation.ui.components.ProcessingPipeline
import org.eu.freex.tools.modules.image.presentation.ui.components.ProjectExplorer
import org.eu.freex.tools.modules.image.presentation.ui.components.inspector.InspectorPanel
import org.eu.freex.tools.modules.image.presentation.ui.components.inspector.core.LocalImageViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.ImageViewModel

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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background // 自动适配深/浅
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Row(modifier = Modifier.fillMaxSize()) {

                // --- 左侧 ---
                ProjectExplorer(
                    modifier = Modifier.width(260.dp).fillMaxHeight(),
                    sourceImages = state.sourceImages,
                    selectedIndex = state.selectedSourceIndex,
                    onSelect = { viewModel.handleEvent(SelectSourceImage(it)) },
                    onImportFile = { file -> viewModel.handleEvent(LoadFile(file)) },
                    onScreenCapture = { viewModel.handleEvent(StartScreenCapture) },
                    onRemove = { viewModel.handleEvent(RemoveSourceImage(it)) }
                )

                // --- 中间 ---
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh) // 【修改】稍微深/浅一点的背景以突显画布
                    ) {
                        EditorCanvas(
                            modifier = Modifier.fillMaxSize(),
                            workImage = state.activeDisplayImage,
                            binaryPreview = state.binaryPreview,
                            scale = state.mainScale,
                            offset = state.mainOffset,
                            hoverColor = state.hoverColor,
                            hoverPos = state.hoverPixelPos,
                            onTransformChange = { s, o ->
                                viewModel.handleEvent(UpdateCanvasTransform(s, o))
                            },
                            onHover = { pos, color ->
                                val fixedPos = pos?.toOffset() ?: Offset.Zero
                                viewModel.handleEvent(HoverCanvas(fixedPos, color))
                            },
                            onColorPick = { hex ->
                                viewModel.handleEvent(ColorPick(hex))
                            }
                        )
                    }
                    ProcessingPipeline(
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                        processChain = state.displayChain,
                        selectedIndex = state.selectedPipelineIndex,
                        onSelect = { viewModel.handleEvent(SelectPipelineStep(it)) },
                        onDelete = { viewModel.handleEvent(DeletePipelineStep(it)) }
                    )
                }

                // --- 右侧 ---
                CompositionLocalProvider(LocalImageViewModel provides viewModel) {
                    InspectorPanel(
                        modifier = Modifier.width(320.dp).fillMaxHeight(),
                        selectedTab = state.rightPanelTabIndex,
                        onTabChange = { viewModel.handleEvent(ChangePanelTab(it)) },
                    )
                }


            }

            // --- 全局弹窗层 ---
            if (state.isScreenCropperVisible && state.fullScreenCapture != null) {
                ScreenCropperDialog(
                    fullScreenImage = state.fullScreenCapture!!,
                    onDismiss = { viewModel.handleEvent(DismissDialogs) },
                    onCropConfirm = { cropped ->
                        viewModel.handleEvent(
                            ConfirmScreenCrop(
                                cropped
                            )
                        )
                    }
                )
            }

            if (state.isMappingDialogVisible && state.mappingBitmap != null) {
                CharMappingDialog(
                    bitmap = state.mappingBitmap!!.toComposeImageBitmap(),
                    onDismiss = { viewModel.handleEvent(DismissDialogs) },
                    onConfirm = { char -> viewModel.handleEvent(ConfirmMapping(char)) }
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
                        color = MaterialTheme.colorScheme.surfaceContainerHighest, // 【修改】
                        tonalElevation = 6.dp, // M3 使用 tonalElevation
                        shadowElevation = 6.dp
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(8.dp),
                            color = MaterialTheme.colorScheme.primary // 【修改】
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


}