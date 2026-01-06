package org.eu.freex.tools.modules.image.presentation.core

import org.eu.freex.tools.modules.image.domain.model.ImageLayer
import org.eu.freex.tools.modules.image.domain.model.ProcessingChain
import org.eu.freex.tools.modules.image.domain.model.font.FontGenerator

data class ImageUiState(
    val isLoading: Boolean = false,
    val assets: List<ImageLayer> = emptyList(),
    val activeChain: ProcessingChain? = null,
    val fontGenerator: FontGenerator? = null,

    // 临时状态
    val cropperLayer: ImageLayer? = null,
    val previewLayer: ImageLayer? = null
) {
    val displayImage: ImageLayer?
        get() {
            if (cropperLayer != null) return cropperLayer
            if (fontGenerator != null) return fontGenerator.inputLayer
            if (previewLayer != null) return previewLayer
            if (activeChain != null) return activeChain.getActiveLayer(assets)
            return assets.firstOrNull()
        }
}