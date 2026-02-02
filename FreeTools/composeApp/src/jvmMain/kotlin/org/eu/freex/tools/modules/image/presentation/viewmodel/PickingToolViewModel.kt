package org.eu.freex.tools.modules.image.presentation.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.eu.freex.tools.common.model.PickEvent
import org.eu.freex.tools.common.model.PickingToolState
import org.eu.freex.tools.common.utils.ColorMatcher
import org.eu.freex.tools.common.utils.toHexString
import org.eu.freex.tools.modules.image.presentation.viewmodel.model.PickRecord
import org.eu.freex.tools.modules.image.presentation.viewmodel.model.PreviewState
import java.awt.image.BufferedImage

class PickingToolViewModel : ViewModel() {

    // === 1. 工具状态 ===
    private val _currentTool = MutableStateFlow<PickingToolState>(PickingToolState.None)
    val currentTool = _currentTool.asStateFlow()

    // === 2. 核心数据：取色记录列表 (驱动右下角列表和中间的标记点) ===
    private val _pickedRecords = MutableStateFlow<List<PickRecord>>(emptyList())
    val pickedRecords = _pickedRecords.asStateFlow()

    // === 3. 核心数据：实时预览 (驱动右上角放大镜) ===
    private val _previewState = MutableStateFlow(PreviewState())
    val previewState = _previewState.asStateFlow()

    // === 4. 事件流 (用于通知 UI 进行一次性操作，如 Toast 提示) ===
    private val _pickEvent = MutableSharedFlow<PickEvent>()
    val pickEvent: SharedFlow<PickEvent> = _pickEvent.asSharedFlow()

    // 原图 (用户框选的那一块)
    private var _targetRegionRaw: BufferedImage? = null
    // 二值化后的图 (用于 UI 显示)
    private val _binaryResultState = MutableStateFlow<ImageBitmap?>(null)
    val binaryResultState = _binaryResultState.asStateFlow()

    // --- Actions ---

    fun activateTool(tool: PickingToolState) {
        _currentTool.value = tool
    }

    /**
     * 处理画布的 Hover 事件 (由 RulerCanvasContainer 调用)
     * 更新右上角的预览图
     */
    fun updatePreview(x: Int, y: Int, color: Color, magnifier: ImageBitmap?) {
        _previewState.update {
            it.copy(
                hasContent = true,
                hoverX = x,
                hoverY = y,
                hoverColor = color,
                magnifierBitmap = magnifier
            )
        }
    }

    fun clearPreview() {
        _previewState.update { it.copy(hasContent = false) }
    }

    /**
     * 设置目标区域 (当用户在画布上框选结束时调用)
     */
    fun setTargetRegion(image: BufferedImage?) {
        _targetRegionRaw = image
        recalculateBinarization() // 立即计算一次
    }

    /**
     * 重新计算二值化
     * (当 setTargetRegion 调用，或者 pickedRecords 发生变化时调用)
     */
    private fun recalculateBinarization() {
        val raw = _targetRegionRaw ?: return
        val records = _pickedRecords.value

        viewModelScope.launch(Dispatchers.Default) {
            // 如果没有取色点，全黑；如果有，开始计算
            val result = if (records.isEmpty()) {
                // 或者显示全黑，或者显示原图灰度，看需求。这里暂定全黑表示“没匹配到任何东西”
                // 也可以逻辑设定为：没选颜色时显示原图方便看
                generateBinaryImage(raw, emptyList())
            } else {
                generateBinaryImage(raw, records)
            }
            _binaryResultState.value = result
        }
    }

    /**
     * 处理取色点击事件 (由 ToolLayer 调用)
     * 不仅仅是 emit event，更重要的是存入 List
     */
    fun emitColorPick(x: Int, y: Int, color: Color) {
        viewModelScope.launch {
            // 1. 发送事件 (保持兼容性，也许有其他地方监听)
            _pickEvent.emit(PickEvent.ColorPicked(x, y, color, color.toHexString(false)))

            // 2. 存入记录列表
            val newIndex = (_pickedRecords.value.maxOfOrNull { it.index } ?: 0) + 1
            val newRecord = PickRecord(
                index = newIndex,
                x = x,
                y = y,
                color = color
            )
            _pickedRecords.update { it + newRecord }

            recalculateBinarization()
        }
    }
    fun emitPointPick(x: Int, y: Int) {
        viewModelScope.launch {
            _pickEvent.emit(
                PickEvent.PointPicked(x, y)
            )
        }
    }

    fun removeRecord(id: String) {
        _pickedRecords.update { list -> list.filterNot { it.id == id } }

        recalculateBinarization()
    }

    /**
     * 核心算法：针对特定图片进行二值化
     */
    private fun generateBinaryImage(image: BufferedImage, records: List<PickRecord>): ImageBitmap {
        val width = image.width
        val height = image.height
        val binaryImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = image.getRGB(x, y)
                val javaColor = java.awt.Color(pixel, true)
                val pixelColor = Color(javaColor.red, javaColor.green, javaColor.blue, javaColor.alpha)

                var isMatch = false
                for (record in records) {
                    // 使用之前的 ColorMatcher 工具
                    if (ColorMatcher.isMatch(record.color, pixelColor, record.offsetColor)) {
                        isMatch = true
                        break
                    }
                }

                // 匹配显示白色，不匹配显示黑色
                val resultColor = if (isMatch) java.awt.Color.WHITE.rgb else java.awt.Color.BLACK.rgb
                binaryImage.setRGB(x, y, resultColor)
            }
        }
        return binaryImage.toComposeImageBitmap()
    }

}