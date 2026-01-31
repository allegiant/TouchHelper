package org.eu.freex.tools.modules.image.presentation.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.eu.freex.tools.common.model.PickingType
import org.eu.freex.tools.common.utils.toHexString

data class PickedData(
    val type: PickingType,
    val x: Int,
    val y: Int,
    val color: Color, // [修改] 直接保留 Color 对象方便使用
    val colorHex: String,
    val timestamp: Long = System.currentTimeMillis()
)

class PickingToolViewModel : ViewModel() {

    private val _isActive = MutableStateFlow(false)
    val isActive = _isActive.asStateFlow()

    private val _pickingMode = MutableStateFlow(PickingType.COLOR)
    val pickingMode = _pickingMode.asStateFlow()

    private val _magnification = MutableStateFlow(12)
    val magnification = _magnification.asStateFlow()

    private val _history = MutableStateFlow<List<PickedData>>(emptyList())
    val history = _history.asStateFlow()

    // [新增] 实时事件流，用于桥接老代码
    private val _pickEvent = MutableSharedFlow<PickedData>()
    val pickEvent: SharedFlow<PickedData> = _pickEvent.asSharedFlow()

    fun setToolActive(active: Boolean) {
        _isActive.value = active
    }

    fun setMode(mode: PickingType) {
        _pickingMode.value = mode
        if (mode != PickingType.NONE) {
            _isActive.value = true
        } else {
            _isActive.value = false
        }
    }

    fun setMagnification(level: Int) {
        _magnification.value = level.coerceIn(2, 32)
    }

    // [新增] 专用取色入口
    fun triggerColorPick(x: Int, y: Int, color: Color) {
        val data = PickedData(
            type = PickingType.COLOR,
            x = x,
            y = y,
            color = color,
            colorHex = color.toHexString()
        )
        // 记录历史 + 发送事件
        _history.update { listOf(data) + it }
        viewModelScope.launch { _pickEvent.emit(data) }
    }

    // [新增] 专用取点入口
    fun triggerPointPick(x: Int, y: Int) {
        val data = PickedData(
            type = PickingType.POINT,
            x = x,
            y = y,
            color = Color.Transparent, // 取点不关心颜色，给个默认值
            colorHex = ""
        )
        // 记录历史 + 发送事件
        _history.update { listOf(data) + it }
        viewModelScope.launch { _pickEvent.emit(data) }
    }

    fun recordPick(x: Int, y: Int, color: Color) {
        val type = _pickingMode.value
        if (type == PickingType.NONE) return

        val data = PickedData(
            type = type,
            x = x,
            y = y,
            color = color,
            colorHex = color.toHexString()
        )

        // 1. 存历史
        _history.update { listOf(data) + it }

        // 2. 发广播
        viewModelScope.launch {
            _pickEvent.emit(data)
        }
    }

    fun clearHistory() {
        _history.value = emptyList()
    }
}