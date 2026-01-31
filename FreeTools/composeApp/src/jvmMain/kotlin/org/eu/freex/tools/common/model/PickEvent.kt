/* Path: common/model/PickEvent.kt */
package org.eu.freex.tools.common.model

import androidx.compose.ui.graphics.Color

/**
 * 工具操作产生的事件
 * 替代了原有的 PickedData 数据类
 */
sealed interface PickEvent {
    // 取色结果
    data class ColorPicked(
        val x: Int,
        val y: Int,
        val color: Color,
        val hex: String
    ) : PickEvent

    // 取点结果
    data class PointPicked(
        val x: Int,
        val y: Int
    ) : PickEvent
}