package org.eu.freex.tools.modules.image.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- 滤镜 ---
@Serializable
sealed interface ImageFilter {
    val name: String
}

@Serializable @SerialName("ORIGIN")
object ViewFilter : ImageFilter { override val name = "原图" }

@Serializable @SerialName("BINARIZATION")
data class BinarizationFilter(
    val min: Float = 0f, val max: Float = 72f, val isRgbAvg: Boolean = true
) : ImageFilter { override val name = "二值化" }

@Serializable @SerialName("GRAYSCALE")
object GrayscaleFilter : ImageFilter { override val name = "灰度化" }

@Serializable @SerialName("COLOR_INVERT")
object ColorInvertFilter : ImageFilter { override val name = "颜色反转" }

@Serializable @SerialName("DENOISE")
data class DenoiseFilter(val radius: UInt = 1u) : ImageFilter { override val name = "去噪" }

@Serializable @SerialName("BW_INVERT")
object BlackWhiteInvertFilter : ImageFilter { override val name = "黑白反转" }

// --- 切割 ---
@Serializable
sealed interface Segmentation {
    val name: String
}

@Serializable @SerialName("GRID")
data class GridSegmentation(
    val rowCount: Int = 1, val colCount: Int = 1
) : Segmentation { override val name = "网格切割" }

@Serializable @SerialName("AUTO")
object AutoSegmentation : Segmentation { override val name = "自动识别" }