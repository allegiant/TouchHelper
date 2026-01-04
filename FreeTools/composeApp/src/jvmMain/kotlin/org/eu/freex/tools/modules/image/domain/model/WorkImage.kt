package org.eu.freex.tools.modules.image.domain.model

import java.awt.image.BufferedImage
import java.util.UUID

/**
 * 工作图像数据模型
 * 【优化】移除了 ImageBitmap 字段，避免内存双重占用。
 * ImageBitmap 应仅在 UI 层通过 remember { bufferedImage.toComposeImageBitmap() } 生成。
 */
data class WorkImage(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val bufferedImage: BufferedImage,
    val label: String = "", // 用于流水线显示的步骤名
    val appliedFilter: ImageFilter? = null,
    // --- 【新增】文件路径 ---
    // 如果是导入的图片，存绝对路径；如果是生成的中间图，为 null
    val path: String? = null
) {
    // 辅助属性：判断是否是二值化图片 (通过参数或 Label 判断)
    val isBinary: Boolean
        get() = label == "二值化" || label.contains("Binary")
}