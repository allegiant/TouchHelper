package org.eu.freex.tools.modules.image.domain.model

import java.awt.image.BufferedImage
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class ImageLayer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,

    // 图片数据不序列化，加载时恢复
    @Transient val image: BufferedImage? = null,

    val config: LayerConfig = LayerConfig.Origin(""),
    val isDirty: Boolean = false
) {
    val activeFilter: ImageFilter? get() = (config as? LayerConfig.Filter)?.filter
}