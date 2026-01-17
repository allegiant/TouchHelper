package org.eu.freex.tools.modules.image.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Pipeline(
    val inputAssetId: String,
    val steps: List<ImageLayer> = emptyList(),
    val activeIndex: Int = -1
) {
    fun getActiveLayer(assets: List<ImageLayer>): ImageLayer? {
        if (activeIndex == -1) return assets.find { it.id == inputAssetId }
        return steps.getOrNull(activeIndex)
    }
}