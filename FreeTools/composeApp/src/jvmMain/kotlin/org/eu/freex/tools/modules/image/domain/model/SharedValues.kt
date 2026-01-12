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
@SerialName("DENOISE")
data class DenoiseFilter(val radius: UInt = 1u) : ImageFilter {
    override val name = "去噪"
}

@Serializable
@SerialName("REMOVE_NOISE")
data class RemoveNoiseFilter(
    val minArea: Int = 6,      // 默认阈值 6
    val gap: Int = 0,          // 默认间隙 0
    val removeWhite: Boolean = true // 默认去除白色
) : ImageFilter {
    override val name = "消除杂点"
}
@Serializable
@SerialName("REMOVE_LINES")
data class RemoveLinesFilter(
    val minLength: Int = 30, // 线条的最小长度 (核的大小)。长度小于此值的线条不会被去除
    val removeHorizontal: Boolean = true, // 是否去除横线
    val removeVertical: Boolean = false, // 是否去除竖线
): ImageFilter {
    override val name = "去直线"
}

@Serializable
@SerialName("EXTRACT_CONTOURS") // 确保序列化名称唯一
data class ExtractContoursFilter(
    // 模式开关：True 为 Canny，False 为 形态学
    val isCanny: Boolean = true,
    // Canny 专用参数
    val cannyLow: Float = 50f,
    val cannyHigh: Float = 150f,
    // 形态学专用参数 (线条粗细)
    val morphKernel: Int = 1
) : ImageFilter {
    override val name: String = "提取轮廓"
}

@Serializable
@SerialName("EXTRACT_BLOBS") // 确保序列化名称唯一
data class ExtractBlobsFilter(
    val minWidth: Float = 0f,
    val maxWidth: Float = 1000f,
    val minHeight: Float = 0f,
    val maxHeight: Float = 1000f,
    val minArea: Float = 0f,
    val maxArea: Float = 10000f,

    val limitWidth: Float = 200f,  // UI滑块上限
    val limitHeight: Float = 200f, // UI滑块上限
    val limitArea: Float = 500f,   // UI滑块上限

    val useEightConnectivity: Boolean = true // [新增字段] 默认为 true (8向)
) : ImageFilter {
    override val name = "提取色块"
}

@Serializable
@SerialName("Deskew") // 如果你使用了多态序列化
data class DeskewFilter(
    // 自动检测开关
    val isAuto: Boolean = false,
    // 手动角度 (度)，正数为顺时针，负数为逆时针
    val angle: Float = 0f,
    // 填充背景色 (0=黑, 255=白)
    val splitBackgroundColor: Boolean = false
) : ImageFilter {
    override val name = "倾斜矫正"
    // 辅助属性，用于 UI 转换背景色 bool 到 int
    val bgColor: Int get() = if (splitBackgroundColor) 255 else 0
}

@Serializable
@SerialName("RotationFilter")
data class RotationFilter(
    val isAuto: Boolean = true,
    // 手动模式参数
    val angle: Float = 0f,
    // 自动模式参数
    val maxSearchRange: Float = 30f,
    val precision: Float = 0.5f
) : ImageFilter {
    override val name = "旋转纠正"
}

@Serializable
@SerialName("BlackWhiteInvert")
data class BlackWhiteInvertFilter(
    // 0: 统一为白底黑字 (推荐), 1: 统一为黑底白字, 2: 强制反色
    val mode: Int = 0
) : ImageFilter {
    override val name = "黑白反转"
}

// 1. 新增枚举：对应 Rust 里的逻辑
@Serializable
@SerialName("MorphologyMode")
enum class MorphologyMode {
    DILATE,   // 膨胀 (扩张白色)
    ERODE,    // 腐蚀 (收缩白色)
    OPEN,     // 开运算 (先腐蚀后膨胀 -> 去噪)
    CLOSE,    // 闭运算 (先膨胀后腐蚀 -> 连笔)
    GRADIENT, // 形态学梯度 (膨胀 - 腐蚀 -> 轮廓)
}

@SerialName("MorphologyFilter")
data class MorphologyFilter(
    val mode: MorphologyMode = MorphologyMode.DILATE,
    val kernelSize: Int = 1, // 核大小 (半径)，实际大小 = 2*r + 1
    val iterations: Int = 1 // 迭代次数
): ImageFilter {
    override val name = "形态学"
}

@Serializable
@SerialName("SmartLayoutFilter")
data class SmartLayoutFilter(
    val padding: Int = 10,
    val minWidth: Int = 5,
    val minHeight: Int = 5,
    val fixedHeight: Int = 0, // 0 表示自动适应
    val alignCenter: Boolean = true
) : ImageFilter {
    override val name = "智能重排"
}
@Serializable
@SerialName("AutoCropMode")
enum class AutoCropMode {
    AUTO_CORNERS, // 自动取角落
    FIXED_COLOR   // 固定颜色
}
@Serializable
@SerialName("SmartLayoutFilter")
data class AutoCropFilter(
    val mode: AutoCropMode = AutoCropMode.AUTO_CORNERS,
    val tolerance: Float = 10f,      // 颜色容差 0-255
    val padding: Int = 1,            // 留白
    val noiseThreshold: Int = 3,     // 抗噪阈值：至少连续3个非背景点才算内容
    val fixedColorHex: String = "#000000" // 如果是手动模式
) : ImageFilter {
    override val name = "智能裁切"
}


/**
 * 缩放滤镜参数
 */
@Serializable
@SerialName("ResizeScaleFilter")
data class ResizeScaleFilter(
    val scaleFactor: Float = 1.0f,  // 缩放倍率 (例如 0.5, 2.0)
    val highQuality: Boolean = true, // true=Lanczos3(平滑), false=Nearest(硬边)
) : ImageFilter {
    override val name = "智能缩放"
}

// ==========================================
// [新增] 切割与识别 - 领域模型
// ==========================================

// ... (保留原有的 ImageFilter 等定义)

// ==========================================
// [新增] 切割与识别 - 领域模型
// ==========================================

@Serializable
data class SegmentationRect(
    val left: Int,
    val top: Int,
    val width: UInt,
    val height: UInt
)

@Serializable
enum class SegmentationMode {
    FIXED_GRID,      // 固定网格
    PROJECTION,      // 投影切割
    CONNECTED_COMP   // 连通区域
}

@Serializable
data class SegmentationConfig(
    val mode: SegmentationMode = SegmentationMode.FIXED_GRID,

    // --- 通用参数 ---
    val padding: Int = 0,
    val minWidth: UInt = 10u,
    val minHeight: UInt = 10u,
    val maxWidth: UInt = 0u,
    val maxHeight: UInt = 0u,

    val mergeDistance: UInt = 0u,            // 新增：0代表不合并

    // --- FixedGrid 参数 ---
    val startX: Int = 0,
    val startY: Int = 0,
    val cellWidth: UInt = 32u,
    val cellHeight: UInt = 32u,
    val colCount: UInt = 1u,
    val rowCount: UInt = 1u,
    val colGap: Int = 0,
    val rowGap: Int = 0,

    // --- Projection 参数 ---
    val splitRows: Boolean = true,
    val splitCols: Boolean = true,
    val projectionThreshold: UByte = 128u
)

