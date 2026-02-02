package org.eu.freex.tools.modules.image.presentation.viewmodel.model

import androidx.compose.ui.graphics.Color
import java.util.UUID

/**
 * 单条取色记录 (对应界面右下角的列表行)
 */
data class PickRecord(
    val id: String = UUID.randomUUID().toString(),
    val index: Int,          // 序号: 1, 2, 3...
    val x: Int,
    val y: Int,
    val color: Color,
    // 针对该点的配置
    val similarity: Float = 0.9f,   // 相似度
    val offsetColor: String = "000000", // 偏色 (16进制字符串)
    val isChecked: Boolean = true   // 是否参与生成脚本
)