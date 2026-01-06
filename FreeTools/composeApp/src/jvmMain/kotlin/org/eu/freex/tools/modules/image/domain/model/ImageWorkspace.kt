package org.eu.freex.tools.modules.image.domain.model

import kotlinx.serialization.Serializable
import org.eu.freex.tools.modules.image.domain.model.font.FontGenerator

@Serializable
data class ImageWorkspace(
    val assets: List<ImageLayer> = emptyList(),
    val activeChain: ProcessingChain? = null,
    val fontGenerator: FontGenerator? = null
) {
    val displayImage: ImageLayer?
        get() {
            if (fontGenerator != null) return fontGenerator.inputLayer
            if (activeChain != null) return activeChain.getActiveLayer(assets)
            return assets.firstOrNull()
        }
}