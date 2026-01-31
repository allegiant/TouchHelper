package org.eu.freex.tools.modules.image.presentation.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.eu.freex.tools.common.model.PickEvent
import org.eu.freex.tools.common.model.PickingToolState
import org.eu.freex.tools.common.utils.toHexString

/**
 * [PickingToolViewModel]
 * 抓抓工具的核心 ViewModel (重构版)
 *
 * 职责：
 * 1. 管理当前激活的工具状态 (State: None / ColorPicker / PointPicker)
 * 2. 接收来自工具图层 (Layer) 的操作数据
 * 3. 向界面 (Workbench) 发送采集事件 (Event)
 */
class PickingToolViewModel : ViewModel() {

    // === 1. 状态管理 ===

    // 当前激活的工具
    // 替代了原来的 _pickingMode 和 _isActive
    private val _currentTool = MutableStateFlow<PickingToolState>(PickingToolState.None)
    val currentTool = _currentTool.asStateFlow()

    // 工具的放大倍率 (保留配置项，供放大镜使用)
    private val _magnification = MutableStateFlow(12)
    val magnification = _magnification.asStateFlow()

    // === 2. 事件流 ===

    // 采集结果事件 (多态事件)
    // 替代了原来的 PickedData
    private val _pickEvent = MutableSharedFlow<PickEvent>()
    val pickEvent: SharedFlow<PickEvent> = _pickEvent.asSharedFlow()

    // === 3. 状态控制方法 ===

    /**
     * 激活指定工具
     * 例如：activateTool(PickingToolState.ColorPicker)
     */
    fun activateTool(tool: PickingToolState) {
        _currentTool.value = tool
    }

    /**
     * 关闭当前工具 (回到空闲状态)
     */
    fun deactivate() {
        _currentTool.value = PickingToolState.None
    }

    fun setMagnification(value: Int) {
        _magnification.value = value.coerceIn(1, 32)
    }

    // === 4. 数据采集入口 (供 Layer 调用) ===

    /**
     * 触发取色事件
     * @param color: 获取到的颜色对象
     */
    fun emitColorPick(x: Int, y: Int, color: Color) {
        viewModelScope.launch {
            _pickEvent.emit(
                PickEvent.ColorPicked(
                    x = x,
                    y = y,
                    color = color,
                    hex = color.toHexString()
                )
            )
        }
    }

    /**
     * 触发取点事件
     * (取点不需要颜色信息，性能更高)
     */
    fun emitPointPick(x: Int, y: Int) {
        viewModelScope.launch {
            _pickEvent.emit(
                PickEvent.PointPicked(
                    x = x,
                    y = y
                )
            )
        }
    }
}