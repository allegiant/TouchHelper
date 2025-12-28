package org.eu.freex.tools.model

import java.awt.image.BufferedImage

/**
 * 工作图像数据模型
 * 【优化】移除了 ImageBitmap 字段，避免内存双重占用。
 * ImageBitmap 应仅在 UI 层通过 remember { bufferedImage.toComposeImageBitmap() } 生成。
 */
data class WorkImage(
    val bufferedImage: BufferedImage,
    val name: String,
    val label: String = "", // 用于流水线显示的步骤名
    val params: Map<String, Any>? = null, // 记录产生此图的参数
) {
    // 辅助属性：判断是否是二值化图片 (通过参数或 Label 判断)
    val isBinary: Boolean
        get() = label == "二值化" || label.contains("Binary")
}