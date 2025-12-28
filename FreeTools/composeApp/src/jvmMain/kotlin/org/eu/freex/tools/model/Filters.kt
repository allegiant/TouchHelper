package org.eu.freex.tools.model

import androidx.compose.ui.graphics.Color
import uniffi.touch_core.BlackWhiteFilterType
import uniffi.touch_core.ColorFilterType
import uniffi.touch_core.CommonFilterType
import uniffi.touch_core.ImageFilter

/**
 * 【重构版】Filters.kt
 * 不再定义类（使用 Uniffi 生成的 Rust 类型），
 * 而是通过扩展属性为生成的枚举/密封类添加 UI 所需的元数据（Label, Description）。
 */

// --- 1. 为生成的 ImageFilter (Sealed Class) 添加扩展 ---

val ImageFilter.label: String
    get() = when (this) {
        is ImageFilter.Color -> this.v1.label
        is ImageFilter.BlackWhite -> this.v1.label
        is ImageFilter.Common -> this.v1.label
        is ImageFilter.View -> "浏览模式"
    }

val ImageFilter.description: String
    get() = when (this) {
        is ImageFilter.Color -> this.v1.description
        is ImageFilter.BlackWhite -> this.v1.description
        is ImageFilter.Common -> this.v1.description
        is ImageFilter.View -> "仅查看图像，不做任何处理"
    }

// --- 2. 为生成的 ColorFilterType (Enum) 添加扩展 ---

val ColorFilterType.label: String
    get() = when (this) {
        ColorFilterType.BINARIZATION -> "二值化"
        ColorFilterType.COLOR_PICK -> "颜色选取"
        ColorFilterType.POSTERIZE -> "色调分离"
        ColorFilterType.GRAYSCALE -> "灰度"
        ColorFilterType.INVERT -> "反色"
    }

val ColorFilterType.description: String
    get() = when (this) {
        ColorFilterType.BINARIZATION -> "根据阈值将图像转换为纯黑白图像"
        ColorFilterType.COLOR_PICK -> "提取图像中指定颜色的区域"
        ColorFilterType.POSTERIZE -> "减少色彩数量，产生分层效果"
        ColorFilterType.GRAYSCALE -> "去除色彩信息，保留亮度信息"
        ColorFilterType.INVERT -> "彩色反转"
    }

// --- 3. 为生成的 BlackWhiteFilterType (Enum) 添加扩展 ---

val BlackWhiteFilterType.label: String
    get() = when (this) {
        BlackWhiteFilterType.DENOISE -> "清除杂点"
        BlackWhiteFilterType.REMOVE_LINES -> "去掉直线"
        BlackWhiteFilterType.CONTOURS -> "获取轮廓"
        BlackWhiteFilterType.EXTRACT_BLOBS -> "提取色块"
        BlackWhiteFilterType.DESKEW -> "倾斜矫正"
        BlackWhiteFilterType.ROTATE_CORRECT -> "旋转纠正"
        BlackWhiteFilterType.INVERT -> "颠倒颜色"
        BlackWhiteFilterType.DILATE_ERODE -> "膨胀腐蚀"
        BlackWhiteFilterType.SKELETON -> "细化抽骨"
        BlackWhiteFilterType.FENCE_ADJUST -> "栅栏调整"
        BlackWhiteFilterType.VALID_IMAGE -> "有效图裁切"
        BlackWhiteFilterType.KEEP_SIZE -> "原尺寸输出"
    }

val BlackWhiteFilterType.description: String
    get() = when (this) {
        BlackWhiteFilterType.DENOISE -> "去除图像中的噪点和孤立像素"
        BlackWhiteFilterType.REMOVE_LINES -> "识别并移除干扰直线"
        BlackWhiteFilterType.CONTOURS -> "提取图像内容的边缘轮廓"
        BlackWhiteFilterType.EXTRACT_BLOBS -> "检测并提取连通的像素区域"
        BlackWhiteFilterType.DESKEW -> "自动校正图像的倾斜角度"
        BlackWhiteFilterType.ROTATE_CORRECT -> "根据内容方向进行旋转修正"
        BlackWhiteFilterType.INVERT -> "黑白反转（底片效果）"
        BlackWhiteFilterType.DILATE_ERODE -> "加粗或变细线条，用于去噪或连接断点"
        BlackWhiteFilterType.SKELETON -> "提取线条的中心骨架"
        BlackWhiteFilterType.FENCE_ADJUST -> "去除类似栅栏的周期性干扰"
        BlackWhiteFilterType.VALID_IMAGE -> "自动裁切掉周围的空白区域"
        BlackWhiteFilterType.KEEP_SIZE -> "保持原始尺寸，不进行裁切"
    }

// --- 4. 为生成的 CommonFilterType (Enum) 添加扩展 ---

val CommonFilterType.label: String
    get() = when (this) {
        CommonFilterType.SCALE_RATIO -> "按比例缩放"
        CommonFilterType.SCALE_NORM -> "归一化缩放"
        CommonFilterType.FIXED_ROTATE -> "固定旋转"
        CommonFilterType.EXTEND_CROP -> "扩展裁切"
        CommonFilterType.FIXED_SMOOTH -> "固定平滑"
        CommonFilterType.MEDIAN_BLUR -> "中值滤波"
    }

val CommonFilterType.description: String
    get() = when (this) {
        CommonFilterType.SCALE_RATIO -> "按指定比例调整图像大小"
        CommonFilterType.SCALE_NORM -> "缩放到标准尺寸"
        CommonFilterType.FIXED_ROTATE -> "旋转固定角度（如90度）"
        CommonFilterType.EXTEND_CROP -> "向外扩展裁切区域"
        CommonFilterType.FIXED_SMOOTH -> "使用固定参数进行平滑处理"
        CommonFilterType.MEDIAN_BLUR -> "非线性滤波，有效去除椒盐噪声"
    }

// --- 5. 常量定义 ---
object FilterConstantsUI {
    const val TITLE_COLOR = "针对彩色进行处理:"
    const val TITLE_BW = "针对黑白进行处理:"
    const val TITLE_COMMON = "通用预处理:"

    val THEME_COLOR = Color(0xFFFF8A80)
}