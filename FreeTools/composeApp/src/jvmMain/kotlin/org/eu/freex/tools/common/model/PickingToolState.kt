package org.eu.freex.tools.common.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.ui.graphics.vector.ImageVector
import java.awt.Cursor

/**
 * 工具状态定义
 * 替代了原有的 PickingType 枚举，支持携带参数
 */
sealed interface PickingToolState {
    val icon: ImageVector
    val desc: String
    // 是否允许画布平移
    // 默认为 true，大多数工具（如吸管、指针）都应该允许用户拖拽移动画布
    val enablePan: Boolean get() = true
    val cursor: Int get() =  Cursor.DEFAULT_CURSOR //光标样式
    val showMagnifier: Boolean get() = false

    // 空闲状态
    data object None : PickingToolState {
        override val icon: ImageVector get() = Icons.Default.NearMe
        override val desc: String get() = "N/A"
    }

    // 取色器
    data object ColorPicker : PickingToolState {
        override val icon: ImageVector get() = Icons.Default.Colorize
        override val desc: String get() = "取色"
        override val cursor: Int get() = Cursor.CROSSHAIR_CURSOR
        override val showMagnifier: Boolean get() = true
    }

    // 取点器
    data object PointPicker : PickingToolState {
        override val icon: ImageVector get() = Icons.Default.AdsClick
        override val desc: String get() = "取点"
        override val cursor: Int get() = Cursor.CROSSHAIR_CURSOR
        override val showMagnifier: Boolean get() = true
    }

    // 区域选择/框选工具
    data object RegionPicker : PickingToolState {
        override val icon: ImageVector get() = Icons.Default.Crop
        override val desc: String get() = "裁剪"
        // 框选时，拖拽手势用于画框，所以禁用画布平移
        override val enablePan: Boolean = false
        override val cursor: Int get() = Cursor.CROSSHAIR_CURSOR
    }

    // 未来可扩展：
    // data class Brush(val size: Float) : PickingToolState
}