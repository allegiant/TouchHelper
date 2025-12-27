// 文件路径: allegiant/touchhelper/TouchHelper-master/FreeTools/composeApp/src/jvmMain/kotlin/org/eu/freex/tools/model/Filters.kt
package org.eu.freex.tools.model

import androidx.compose.ui.graphics.Color

interface ImageFilter {
    val label: String
    val description: String // 【新增】说明字段
}

// 【修改】默认的浏览模式
object ViewFilter : ImageFilter {
    override val label: String = "浏览模式"
    override val description: String = "仅查看图像，不做任何处理"
}

// 【修改】为每个枚举添加说明参数
enum class ColorFilterType(override val label: String, override val description: String) : ImageFilter {
    BINARIZATION("二值化", "根据阈值将图像转换为纯黑白图像"),
    COLOR_PICK("颜色选取", "提取图像中指定颜色的区域"),
    POSTERIZE("色调分离", "减少色彩数量，产生分层效果"),
    GRAYSCALE("灰度", "去除色彩信息，保留亮度信息");

    companion object {
        const val TITLE = "针对彩色进行处理:"
        val COLOR = Color(0xFFFF8A80)
    }
}

// 【修改】为每个枚举添加说明参数
enum class BlackWhiteFilterType(override val label: String, override val description: String) : ImageFilter {
    DENOISE("清除杂点", "去除图像中的噪点和孤立像素"),
    REMOVE_LINES("去掉直线", "识别并移除干扰直线"),
    CONTOURS("获取轮廓", "提取图像内容的边缘轮廓"),
    EXTRACT_BLOBS("提取色块", "检测并提取连通的像素区域"),
    DESKEW("倾斜矫正", "自动校正图像的倾斜角度"),
    ROTATE_CORRECT("旋转纠正", "根据内容方向进行旋转修正"),
    INVERT("颠倒颜色", "黑白反转（底片效果）"),
    DILATE_ERODE("膨胀腐蚀", "加粗或变细线条，用于去噪或连接断点"),
    SKELETON("细化抽骨", "提取线条的中心骨架"),
    FENCE_ADJUST("栅栏调整", "去除类似栅栏的周期性干扰"),
    VALID_IMAGE("有效图像", "自动裁剪掉周围的空白区域"),
    KEEP_SIZE("保留大小", "强制图像保持当前尺寸");

    companion object {
        const val TITLE = "针对黑白进行处理:"
        val COLOR = Color(0xFFFFD54F)
    }
}

// 【修改】为每个枚举添加说明参数
enum class CommonFilterType(override val label: String, override val description: String) : ImageFilter {
    SCALE_RATIO("等比缩放", "按固定比例放大或缩小图像"),
    SCALE_NORM("缩放归一化", "缩放到标准尺寸（通常用于模型输入）"),
    FIXED_ROTATE("固定旋转", "按90度、180度等固定角度旋转"),
    EXTEND_CROP("延伸裁剪", "向外扩展或向内裁剪图像边界"),
    FIXED_SMOOTH("固定柔化", "平滑图像边缘，减少锯齿"),
    MEDIAN_BLUR("中值滤波", "一种非线性平滑技术，有效去除椒盐噪声");

    companion object {
        const val TITLE = "通用处理:"
        val COLOR = Color(0xFF81C784)
    }
}