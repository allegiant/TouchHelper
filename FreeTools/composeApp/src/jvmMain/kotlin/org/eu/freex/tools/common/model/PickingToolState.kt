package org.eu.freex.tools.common.model

/**
 * 工具状态定义
 * 替代了原有的 PickingType 枚举，支持携带参数
 */
sealed interface PickingToolState {
    // 空闲状态
    data object None : PickingToolState

    // 取色器
    data object ColorPicker : PickingToolState

    // 取点器
    data object PointPicker : PickingToolState

    // 未来可扩展：
    // data class Brush(val size: Float) : PickingToolState
}