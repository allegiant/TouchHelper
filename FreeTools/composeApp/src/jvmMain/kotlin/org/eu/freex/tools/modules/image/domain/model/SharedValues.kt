package org.eu.freex.tools.modules.image.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.eu.freex.tools.common.ColorRule

// --- 滤镜 ---
@Serializable
sealed interface ImageFilter {
    val name: String
}

@Serializable
@SerialName("ORIGIN")
object ViewFilter : ImageFilter {
    override val name = "原图"
}

// 1. 定义三种模式的枚举
enum class BinarizationMode {
    MANUAL,     // 手动 / RGB平均 (对应截图的 "RGB平均阈值")
    ADAPTIVE,   // 智能 / 局部自适应 (对应截图的 "智能(点数均衡)")
    OTSU        // 自动 / 大津法 (对应截图的 "自动(OTSU算法)")
}

@Serializable
@SerialName("BINARIZATION")
data class BinarizationFilter(
    val mode: BinarizationMode = BinarizationMode.MANUAL,
    val min: Float = 0f,
    val max: Float = 72f,
    val isRgbAvg: Boolean = true,
    val sauvolaK: Float = 0.2f,
    val windowSize: Float = 15f,
) : ImageFilter {
    override val name = "二值化"
}

// [新增] 色彩空间枚举
enum class PosterizationMode {
    RGB, HSV
}

@Serializable
@SerialName("POSTERIZATION")
data class PosterizationFilter(
    // [新增] 模式选择
    val mode: PosterizationMode = PosterizationMode.RGB,

    val isMultiValue: Boolean = true,
    val level: Int = 2,

    // 通道开关 (RGB模式下代表R/G/B，HSV模式下代表H/S/V)
    val channel1: Boolean = false, // 原 extractR
    val channel2: Boolean = false, // 原 extractG
    val channel3: Boolean = false  // 原 extractB
) : ImageFilter {
    override val name = "色调分离"
}


@Serializable
@SerialName("KEEPCOLORS")
data class MultiColorFilter(
    val rules: List<ColorRule> = emptyList(),
    val isInvert: Boolean = false,
    val keepOriginal: Boolean = false
) : ImageFilter {
    override val name = "颜色选取"
}

// 1. 新增枚举：对应 Rust 里的逻辑
@Serializable
enum class GrayscaleMode {
    WEIGHTED, // 标准 (加权平均)
    MAX,      // 最大值 (去黑底)
    MIN,      // 最小值 (去白底)
    RED,      // 红色通道 (去红章)
    GREEN,    // 绿色通道 (高清晰度)
    BLUE      // 蓝色通道
}

// 2. 修改滤镜类：增加 mode 字段，默认为标准模式
@Serializable
@SerialName("GRAYSCALE")
data class GrayscaleFilter(
    val mode: GrayscaleMode = GrayscaleMode.WEIGHTED
) : ImageFilter {
    override val name = "灰度化"
}

@Serializable
@SerialName("COLOR_INVERT")
object ColorInvertFilter : ImageFilter {
    override val name = "颜色反转"
}

@Serializable
@SerialName("DENOISE")
data class DenoiseFilter(val radius: UInt = 1u) : ImageFilter {
    override val name = "去噪"
}

@Serializable
@SerialName("BW_INVERT")
object BlackWhiteInvertFilter : ImageFilter {
    override val name = "黑白反转"
}

// --- 切割 ---
@Serializable
sealed interface Segmentation {
    val name: String
}

@Serializable
@SerialName("GRID")
data class GridSegmentation(
    val rowCount: Int = 1, val colCount: Int = 1
) : Segmentation {
    override val name = "网格切割"
}

@Serializable
@SerialName("AUTO")
object AutoSegmentation : Segmentation {
    override val name = "自动识别"
}