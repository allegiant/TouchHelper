package org.eu.freex.tools.modules.image.presentation.viewmodel

import org.eu.freex.tools.modules.image.presentation.core.*

class AssetDelegate(private val context: ViewModelContext) {

    suspend fun handle(event: AssetEvent) {
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

    private suspend fun loadFile(event: LoadFile) {
        context.useCase.importAsset(event.file).onSuccess { layer ->
            context.updateWorkspace { copy(assets = assets + layer) }
            activateAsset(layer.id)
        }
    }

    private suspend fun selectAsset(event: SelectAsset) {
        activateAsset(event.assetId)
    }

    private fun removeAsset(event: RemoveAsset) {
        context.updateWorkspace { context.useCase.removeAsset(this, event.assetId) }
        context.updateUiState { it.copy(previewLayer = null) }
    }

    private suspend fun saveProject(event: SaveProject) {
        context.useCase.saveWorkspace(event.file, context.getWorkspaceSnapshot())
            .onFailure { it.printStackTrace() }
    }

    private suspend fun loadProject(event: LoadProject) {
        context.useCase.loadWorkspace(event.file).onSuccess { loadedWorkspace ->
            context.updateWorkspace { loadedWorkspace }
            context.updateUiState { it.copy(previewLayer = null) }
        }.onFailure { it.printStackTrace() }
    }

    private suspend fun exportDisplayImage(event: ExportDisplayImage) {
        context.uiState.value.displayImage?.let {
            context.useCase.exportImage(it, event.file)
        }
    }

    private suspend fun exportImage(event: ExportImage) {
        context.useCase.exportImage(event.layer, event.file)
    }

    private suspend fun activateAsset(assetId: String) {
        context.useCase.activateAsset(context.getWorkspaceSnapshot(), assetId)
            .onSuccess { newWorkspace ->
                context.updateWorkspace { newWorkspace }
                context.updateUiState { s -> s.copy(previewLayer = null) }
            }
            .onFailure { it.printStackTrace() }
    }
}