package org.eu.freex.tools.modules.image.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ImageWorkspace(
    val assets: List<ImageLayer> = emptyList(),
    val pipeline: Pipeline? = null,
    val segmentation: SegmentationProject? = null,
    val fontLibrary: List<FontLibItem> = emptyList()
) {
    val displayImage: ImageLayer?
        get() {
            if (pipeline != null) return pipeline.getActiveLayer(assets)
            return assets.firstOrNull()
        }
}