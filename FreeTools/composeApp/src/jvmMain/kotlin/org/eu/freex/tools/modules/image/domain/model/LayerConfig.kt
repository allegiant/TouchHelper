package org.eu.freex.tools.modules.image.domain.model

import kotlinx.serialization.Serializable

@Serializable
// 关键点：将 abstract 改为 sealed
sealed class LayerConfig {

    @Serializable
    // 关键点：如果需要自定义在 JSON 中的名称，可以使用 @SerialName
    // @SerialName("Origin")
    data class Origin(val sourcePath: String) : LayerConfig()

    @Serializable
    // @SerialName("Filter")
    data class Filter(val filter: ImageFilter) : LayerConfig()
}