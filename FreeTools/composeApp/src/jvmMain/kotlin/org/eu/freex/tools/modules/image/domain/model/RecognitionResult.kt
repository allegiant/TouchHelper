package org.eu.freex.tools.modules.image.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class RecognitionResult(
    val char: String,          // 识别出的字符 (例如 "A", "攻")
    val rect: SegmentationRect,// 字符所在的坐标
    val confidence: Float      // 置信度 (0.0 - 1.0)，匹配度有多高
)