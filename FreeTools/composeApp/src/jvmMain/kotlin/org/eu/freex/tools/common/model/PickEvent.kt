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

    // 虽然 RegionPickerLayer 可能直接调用 ViewModel，
    // 但为了满足 Registry 的类型系统，我们定义这个事件。
    data object RegionPicked : PickEvent
}