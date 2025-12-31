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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.modules.image.presentation.contract.events.ConfirmMapping
import org.eu.freex.tools.modules.image.presentation.contract.events.ConfirmScreenCrop
import org.eu.freex.tools.modules.image.presentation.contract.events.DeletePipelineStep
import org.eu.freex.tools.modules.image.presentation.contract.events.DismissDialogs
import org.eu.freex.tools.modules.image.presentation.contract.events.SelectPipelineStep
import org.eu.freex.tools.modules.image.presentation.ui.components.EditorCanvas
import org.eu.freex.tools.modules.image.presentation.ui.components.ProcessingPipeline
import org.eu.freex.tools.modules.image.presentation.ui.components.ProjectExplorer
import org.eu.freex.tools.modules.image.presentation.ui.components.inspector.InspectorPanel
import org.eu.freex.tools.modules.image.presentation.ui.components.inspector.core.LocalImageViewModel
import org.eu.freex.tools.modules.image.presentation.ui.dialogs.CharMappingDialog
import org.eu.freex.tools.modules.image.presentation.ui.dialogs.ScreenCropperDialog
import org.eu.freex.tools.modules.image.presentation.ui.state.rememberEditorState
import org.eu.freex.tools.modules.image.presentation.ui.state.rememberProjectExplorerState
import org.eu.freex.tools.modules.image.presentation.viewmodel.ImageViewModel
import org.koin.compose.koinInject

@Composable
fun ImageWorkbench(
    viewModel: ImageViewModel = koinInject()
) {
    val fullState by viewModel.uiState.collectAsState()

    // --- 状态切片优化 ---
    val projectState by remember(fullState) { derivedStateOf { fullState.project } }
    val pipelineState by remember(fullState) { derivedStateOf { fullState.pipeline } } // 【新增】
    val uiState by remember(fullState) { derivedStateOf { fullState.ui } }

    val explorerState = rememberProjectExplorerState(
        projectState = projectState,
        onEvent = viewModel::handleEvent
    )

    val editorState = rememberEditorState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.uiEffect.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Row(modifier = Modifier.fillMaxSize()) {

                // --- 左侧：项目资源 ---
                ProjectExplorer(
                    modifier = Modifier.width(260.dp).fillMaxHeight(),
                    state = explorerState,
                )

                // --- 中间：画布与流水线 ---
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        EditorCanvas(
                            modifier = Modifier.fillMaxSize(),
                            workImage = fullState.activeDisplayImage,
                            state = editorState
                        )
                    }
                    ProcessingPipeline(
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                        processChain = fullState.displayChain,
                        projectState = projectState,
                        onSelect = { viewModel.handleEvent(SelectPipelineStep(it)) },
                        onDelete = { viewModel.handleEvent(DeletePipelineStep(it)) }
                    )
                }

                // --- 右侧：属性面板 ---
                CompositionLocalProvider(LocalImageViewModel provides viewModel) {
                    InspectorPanel(
                        modifier = Modifier.width(320.dp).fillMaxHeight(),
                        projectState = projectState,
                        pipelineState = pipelineState, // 【修复】传入 PipelineState
                        uiState = uiState,
                    )
                }
            }

            // --- 全局弹窗层 ---
            if (uiState.isScreenCropperVisible && uiState.fullScreenCapture != null) {
                ScreenCropperDialog(
                    fullScreenImage = uiState.fullScreenCapture!!,
                    onDismiss = { viewModel.handleEvent(DismissDialogs) },
                    onCropConfirm = { cropped ->
                        viewModel.handleEvent(ConfirmScreenCrop(cropped))
                    }
                )
            }

            if (uiState.isMappingDialogVisible && uiState.mappingBitmap != null) {
                CharMappingDialog(
                    bitmap = uiState.mappingBitmap!!.toComposeImageBitmap(),
                    onDismiss = { viewModel.handleEvent(DismissDialogs) },
                    onConfirm = { char -> viewModel.handleEvent(ConfirmMapping(char)) }
                )
            }

            // --- Loading 层 ---
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        tonalElevation = 6.dp,
                        shadowElevation = 6.dp
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(8.dp),
                            color = MaterialTheme.colorScheme.primary
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