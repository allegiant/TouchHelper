package org.eu.freex.tools.modules.image.presentation.viewmodel

import org.eu.freex.tools.modules.image.presentation.core.CancelPick
import org.eu.freex.tools.modules.image.presentation.core.ConfirmCrop
import org.eu.freex.tools.modules.image.presentation.core.DismissCropper
import org.eu.freex.tools.modules.image.presentation.core.InteractionEvent
import org.eu.freex.tools.modules.image.presentation.core.PickingType
import org.eu.freex.tools.modules.image.presentation.core.StartScreenCapture
import org.eu.freex.tools.modules.image.presentation.core.TriggerColorPick
import org.eu.freex.tools.modules.image.presentation.core.TriggerPointPick


/**
 * ImageViewModel 的资源管理扩展逻辑
 */
internal suspend fun ImageViewModel.handleInteractionEvent(event: InteractionEvent) {
    when (event) {
        is StartScreenCapture -> startScreenCapture()
        is ConfirmCrop -> confirmCrop(event)
        is DismissCropper -> updateUiState { it.copy(cropperLayer = null) }
        is TriggerColorPick -> sendColorPick(event.color)
        is TriggerPointPick -> sendPointPick(event.point)
        is CancelPick -> updateUiState { it.copy(pickingType = PickingType.NONE) }
    }
}

private suspend fun ImageViewModel.startScreenCapture() {
    useCase.captureScreen().onSuccess { layer ->
        updateUiState { it.copy(cropperLayer = layer, isLoading = false) }
    }
}

private suspend fun ImageViewModel.confirmCrop(event: ConfirmCrop) {
    useCase.cropImage(event.sourceLayer, event.rect).onSuccess { croppedLayer ->
        updateWorkspace { it.copy(assets = it.assets + croppedLayer) }
        useCase.activateAsset(getWorkspaceSnapshot(), croppedLayer.id)
            .onSuccess { finalWs ->
                updateWorkspace { finalWs }
                updateUiState { it.copy(cropperLayer = null) }
            }
    }
}