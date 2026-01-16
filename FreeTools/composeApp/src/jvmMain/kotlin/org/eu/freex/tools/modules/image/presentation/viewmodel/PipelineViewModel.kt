package org.eu.freex.tools.modules.image.presentation.viewmodel

import org.eu.freex.tools.modules.image.domain.model.ImageFilter
import org.eu.freex.tools.modules.image.domain.model.ImageLayer
import org.eu.freex.tools.modules.image.domain.model.LayerConfig
import org.eu.freex.tools.modules.image.presentation.core.ApplyFilterStep
import org.eu.freex.tools.modules.image.presentation.core.CancelPreview
import org.eu.freex.tools.modules.image.presentation.core.PipelineEvent
import org.eu.freex.tools.modules.image.presentation.core.PreviewFilter
import org.eu.freex.tools.modules.image.presentation.core.RemoveStep
import org.eu.freex.tools.modules.image.presentation.core.SelectStep
import org.eu.freex.tools.modules.image.presentation.core.UpdateFilterStep


/**
 * ImageViewModel 的线水线管理扩展逻辑
 */
internal suspend fun ImageViewModel.handlePipelineEvent(event: PipelineEvent) {
    when (event) {
        is PreviewFilter -> triggerPreview(event.filter)
        is CancelPreview -> updateUiState { it.copy(previewLayer = null) }
        is ApplyFilterStep -> applyFilterStep(event.filter)
        is UpdateFilterStep -> updateFilterStep(event.filter)
        is SelectStep -> selectStep(event.index)
        is RemoveStep -> removeStep(event.index)
    }
}

private fun ImageViewModel.triggerPreview(filter: ImageFilter) {
    val workspace = getWorkspaceSnapshot()
    val chain = workspace.pipeline ?: return
    val idx = chain.activeIndex

    // 查找输入源 (Base Layer)
    val baseLayer = if (idx <= 0) {
        workspace.assets.find { it.id == chain.inputAssetId }
    } else {
        chain.steps.getOrNull(idx - 1)
    }

    if (baseLayer?.image != null) {
        val currentImage = uiState.value.previewLayer?.image
            ?: chain.getActiveLayer(workspace.assets)?.image
            ?: baseLayer.image

        updateUiState {
            it.copy(
                previewLayer = ImageLayer(
                    name = "Previewing...",
                    image = currentImage,
                    config = LayerConfig.Filter(filter)
                ),
                isLoading = false
            )
        }
        sendPreviewRequest(baseLayer, filter)
    }
}

private suspend fun ImageViewModel.applyFilterStep(filter: ImageFilter) {
    useCase.addFilterStep(getWorkspaceSnapshot(), filter).onSuccess { newWorkspace ->
        updateWorkspace { newWorkspace } // 直接替换
        updateUiState { s -> s.copy(previewLayer = null) }
    }
}

private suspend fun ImageViewModel.updateFilterStep(filter: ImageFilter) {
    // [修复] 同上
    useCase.updateFilterStep(getWorkspaceSnapshot(), filter).onSuccess { newWorkspace ->
        updateWorkspace { newWorkspace }
        updateUiState { s -> s.copy(previewLayer = null) }
    }
}

private fun ImageViewModel.selectStep(index: Int) {
    updateWorkspace {
        // 这里的 this 就是当前的 Workspace
        it.pipeline?.let { currentChain ->
            it.copy(pipeline = currentChain.copy(activeIndex = index))
        } ?: it
    }
    updateUiState { s -> s.copy(previewLayer = null) }
}

private suspend fun ImageViewModel.removeStep(index: Int) {
    useCase.removeStep(getWorkspaceSnapshot(), index)
        .onSuccess { newWorkspace ->
            updateWorkspace { newWorkspace }
            updateUiState { s -> s.copy(previewLayer = null) }
        }
        .onFailure { it.printStackTrace() }
}