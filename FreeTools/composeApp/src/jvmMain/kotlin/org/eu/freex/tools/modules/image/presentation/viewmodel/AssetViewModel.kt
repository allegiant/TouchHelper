// 文件路径: .../presentation/viewmodel/ImageViewModelAsset.kt
// (与 ImageViewModel 在同一个包)

package org.eu.freex.tools.modules.image.presentation.viewmodel

import org.eu.freex.tools.modules.image.presentation.core.*

/**
 * ImageViewModel 的资源管理扩展逻辑
 * 物理拆分，逻辑上仍属于 ViewModel
 */
internal suspend fun ImageViewModel.handleAssetEvent(event: AssetEvent) {
    when (event) {
        is LoadFile -> loadFile(event)
        is SelectAsset -> selectAsset(event)
        is RemoveAsset -> removeAsset(event)
        is SaveProject -> saveProject(event)
        is LoadProject -> loadProject(event)
        is ExportDisplayImage -> exportDisplayImage(event)
        is ExportImage -> exportImage(event)
    }
}

// 以下均为 ImageViewModel 的私有扩展方法

private suspend fun ImageViewModel.loadFile(event: LoadFile) {
    useCase.importAsset(event.file).onSuccess { layer ->
        updateWorkspace { it.copy(assets = it.assets + layer) }
        activateAsset(layer.id)
    }
}

private suspend fun ImageViewModel.selectAsset(event: SelectAsset) {
    activateAsset(event.assetId)
}

private fun ImageViewModel.removeAsset(event: RemoveAsset) {
    // 这里的 this 指向 ImageWorkspace (lambda receiver)
    updateWorkspace { useCase.removeAsset(it, event.assetId) }
    // 清理预览状态
    updateUiState { it.copy(previewLayer = null) }
}

private suspend fun ImageViewModel.saveProject(event: SaveProject) {
    // 使用 getInternalWorkspace() 获取当前状态
    useCase.saveWorkspace(event.file, getWorkspaceSnapshot())
        .onFailure { it.printStackTrace() }
}

private suspend fun ImageViewModel.loadProject(event: LoadProject) {
    useCase.loadWorkspace(event.file).onSuccess { loadedWorkspace ->
        updateWorkspace { loadedWorkspace }
        updateUiState { it.copy(previewLayer = null) }
    }.onFailure { it.printStackTrace() }
}

private suspend fun ImageViewModel.exportDisplayImage(event: ExportDisplayImage) {
    uiState.value.displayImage?.let {
        useCase.exportImage(it, event.file)
    }
}

private suspend fun ImageViewModel.exportImage(event: ExportImage) {
    useCase.exportImage(event.layer, event.file)
}

private suspend fun ImageViewModel.activateAsset(assetId: String) {
    useCase.activateAsset(getWorkspaceSnapshot(), assetId)
        .onSuccess { newWorkspace ->
            updateWorkspace { newWorkspace }
            updateUiState { s -> s.copy(previewLayer = null) }
        }
        .onFailure { it.printStackTrace() }
}