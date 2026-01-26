package org.eu.freex.tools.modules.image.domain.model

import java.util.UUID

/**
 * 特征点 (用于抓抓功能)
 */
data class FeaturePoint(
    val id: String = UUID.randomUUID().toString(),
    val x: Int,
    val y: Int,
    val colorHex: String, // 例如 "#FF0000"
    val description: String = "" // 用户备注，例如 "确认按钮"
)