package org.eu.freex.tools.modules.image.domain.model

import java.util.UUID

/**
 * 脚本特征点 (用于抓抓功能: 多点找色/区域找色)
 */
data class FeaturePoint(
    val id: String = UUID.randomUUID().toString(),
    val index: Int = 0,           // 序号 (例如 1, 2, 3)
    val x: Int,
    val y: Int,
    val colorHex: String,         // 颜色值 (例如 "#FF0000")
    val tolerance: String = "101010", // 偏色 (默认 101010)
    val isChecked: Boolean = true // 是否参与生成
)