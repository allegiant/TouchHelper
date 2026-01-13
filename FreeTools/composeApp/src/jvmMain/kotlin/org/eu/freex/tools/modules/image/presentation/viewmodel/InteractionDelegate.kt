package org.eu.freex.tools.modules.image.presentation.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.channels.Channel
import org.eu.freex.tools.modules.image.presentation.core.*

class InteractionDelegate(private val context: ViewModelContext) {

    private val colorPickChannel = Channel<Color>(Channel.RENDEZVOUS)
    private val pointPickChannel = Channel<IntOffset>(Channel.RENDEZVOUS)

    suspend fun handle(event: InteractionEvent) {
        when (event) {
            is StartScreenCapture -> startScreenCapture()
            is ConfirmCrop -> confirmCrop(event)
            is DismissCropper -> context.updateUiState { it.copy(cropperLayer = null) }
            is TriggerColorPick -> colorPickChannel.send(event.color)
            is TriggerPointPick -> pointPickChannel.send(event.point)
            is CancelPick -> context.updateUiState { it.copy(pickingType = PickingType.NONE) }
        }
    }

    private suspend fun startScreenCapture() {
        context.useCase.captureScreen().onSuccess { layer ->
            context.updateUiState { it.copy(cropperLayer = layer, isLoading = false) }
        }
    }

    private suspend fun confirmCrop(event: ConfirmCrop) {
        context.useCase.cropImage(event.sourceLayer, event.rect).onSuccess { croppedLayer ->
            context.updateWorkspace { copy(assets = assets + croppedLayer) }
            // 简单起见直接调用 UseCase 激活，也可以考虑复用 AssetDelegate
            context.useCase.activateAsset(context.getWorkspaceSnapshot(), croppedLayer.id)
                .onSuccess { finalWs ->
                    context.updateWorkspace { finalWs }
                    context.updateUiState { it.copy(cropperLayer = null) }
                }
        }
    }

    // --- Exposed Interaction Methods (Called by ViewModel) ---

    suspend fun awaitColorPick(): Color? {
        context.updateUiState { it.copy(pickingType = PickingType.COLOR) }
        return try {
            colorPickChannel.receive()
        } catch (e: Exception) {
            null
        } finally {
            context.updateUiState { it.copy(pickingType = PickingType.NONE) }
        }
    }

    suspend fun awaitPointPick(): IntOffset? {
        context.updateUiState { it.copy(pickingType = PickingType.POINT) }
        return try {
            pointPickChannel.receive()
        } catch (e: Exception) {
            null
        } finally {
            context.updateUiState { it.copy(pickingType = PickingType.NONE) }
        }
    }
}