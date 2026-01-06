package org.eu.freex.tools.modules.image.domain.model

import kotlinx.serialization.Serializable

@Serializable
sealed interface LayerConfig {
    data class Origin(val sourcePath: String) : LayerConfig
    data class Filter(val filter: ImageFilter) : LayerConfig
}