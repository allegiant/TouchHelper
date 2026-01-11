package org.eu.freex.tools.modules.image.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.modules.image.presentation.core.*
import org.eu.freex.tools.modules.image.presentation.features.editor.EditorCanvasPanel
import org.eu.freex.tools.modules.image.presentation.features.filter.components.InspectorPanel
import org.eu.freex.tools.modules.image.presentation.features.pipeline.ProcessingPipeline
import org.eu.freex.tools.modules.image.presentation.features.project.ProjectListPanel
import org.eu.freex.tools.modules.image.presentation.features.tools.dialogs.ScreenCropperDialog
import org.eu.freex.tools.modules.image.presentation.viewmodel.ImageViewModel
import org.koin.compose.koinInject

@Composable
fun ImageWorkbench(
    viewModel: ImageViewModel = koinInject()
) {
    val state by viewModel.uiState.collectAsState()

    CompositionLocalProvider(LocalImageViewModel provides viewModel) {

        state.cropperLayer?.let { layer ->
            ScreenCropperDialog(
                imageLayer = layer,
                onConfirm = { rect -> viewModel.handleEvent(ConfirmCrop(layer, rect)) },
                onDismiss = { viewModel.handleEvent(DismissCropper) }
            )
        }

        Row(modifier = Modifier.fillMaxSize()) {

            // 左侧
            Column(modifier = Modifier.width(260.dp).fillMaxHeight()) {
                Button(
                    onClick = { viewModel.handleEvent(StartScreenCapture) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("屏幕截图")
                }

                ProjectListPanel(
                    modifier = Modifier.weight(1f),
                    assets = state.assets,
                    activeAssetId = state.activeChain?.inputAssetId,
                    onEvent = viewModel::handleEvent
                )
            }

            // 中间：画布与流水线
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                // 1. 画布：占据绝大部分空间
                EditorCanvasPanel(
                    modifier = Modifier.weight(1f),
                    displayLayer = state.displayImage,
                    pickingType = state.pickingType,
                    segmentationResults = state.segmentationProject?.results ?: emptyList(),
                    showSegmentationOverlay = state.activeTab == WorkbenchTab.SEGMENTATION,
                    onPickColor = { viewModel.handleEvent(TriggerColorPick(it)) }, // 传出颜色
                    onPickPoint = { viewModel.handleEvent(TriggerPointPick(it)) }, // 传出坐标
                    onCancel = { viewModel.handleEvent(CancelPick) } // 通用取消
                )

                // 2. 流水线：固定高度，不再抢占空间
                ProcessingPipeline(
                    modifier = Modifier.fillMaxWidth().height(112.dp),
                    chain = state.activeChain,
                    assets = state.assets,
                    onEvent = viewModel::handleEvent
                )
            }

            // 右侧
            InspectorPanel(
                modifier = Modifier.width(320.dp).fillMaxHeight(),
                uiState = state,
                onEvent = viewModel::handleEvent
            )
        }
    }
}