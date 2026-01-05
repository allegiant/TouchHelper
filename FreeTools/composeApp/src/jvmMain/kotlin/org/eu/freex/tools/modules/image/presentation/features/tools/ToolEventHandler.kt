package org.eu.freex.tools.modules.image.presentation.features.tools

import org.eu.freex.tools.modules.image.presentation.core.ImageEventHandler
import org.eu.freex.tools.modules.image.presentation.core.ImageUiEvent
import org.eu.freex.tools.modules.image.presentation.core.ImageUiState
import org.eu.freex.tools.modules.image.presentation.core.ToolEvent
import java.lang.Exception

class ToolEventHandler : ImageEventHandler {

    override suspend fun handle(
        event: ImageUiEvent,
        state: ImageUiState,
        showToast: (String) -> Unit
    ): ImageUiState? {
        // 1. 自动过滤：只处理 ToolEvent
        if (event !is ToolEvent) return null

        // 2. 处理逻辑
        return when (event) {
            is DismissDialogs -> {
                // 原逻辑：setScreenCropper(null)
                // 现逻辑：返回 cropperImage 为 null 的新状态
                state.copy(cropperImage = null)
            }

            else -> {
                throw Exception("Unhandled event: $event")
            }
        }
    }
}