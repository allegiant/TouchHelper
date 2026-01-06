package org.eu.freex.tools.modules.image.domain.model.font

import kotlinx.serialization.Serializable
import org.eu.freex.tools.modules.image.domain.model.ImageLayer
import org.eu.freex.tools.modules.image.domain.model.Segmentation

// 简单的 Rect 替代 java.awt.Rectangle 以支持序列化
@Serializable
data class FontRect(val x: Int, val y: Int, val w: Int, val h: Int)

@Serializable
data class Glyph(
    val id: String,
    val char: String?,
    val layer: ImageLayer,
    val bounds: FontRect
)

@Serializable
data class FontGenerator(
    val inputLayer: ImageLayer,
    val segmentation: Segmentation,
    val glyphs: List<Glyph> = emptyList(),
    val selectedIndex: Int = -1
)