package org.eu.freex.tools.modules.image.presentation.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.eu.freex.tools.common.model.PickingToolState
import org.eu.freex.tools.common.utils.ColorMatcher
import org.eu.freex.tools.common.utils.toHexString
import org.eu.freex.tools.modules.image.domain.model.FeaturePoint
import org.eu.freex.tools.modules.image.domain.model.ImageLayer
import org.eu.freex.tools.modules.image.presentation.viewmodel.model.PreviewState
import java.awt.image.BufferedImage
import java.util.UUID

class PickingToolViewModel : ViewModel() {

    // ============================================================================================
    // State (状态)
    // ============================================================================================

    // 1. 当前激活的工具
    private val _currentTool = MutableStateFlow<PickingToolState>(PickingToolState.None)
    val currentTool = _currentTool.asStateFlow()

    // 2. 当前正在分析的图层 (Session Layer)
    // [修改点]：这里现在持有完整的 ImageLayer 对象，而不仅仅是 Bitmap
    private val _displayImage = MutableStateFlow<ImageLayer?>(null)
    val displayImage = _displayImage.asStateFlow()

    // 3. 特征点列表 (统一使用 FeaturePoint)
    private val _featurePoints = MutableStateFlow<List<FeaturePoint>>(emptyList())
    val featurePoints = _featurePoints.asStateFlow()

    // 4. 预览状态 (用于右侧面板放大镜)
    private val _previewState = MutableStateFlow(PreviewState())
    val previewState = _previewState.asStateFlow()

    // 5. 二值化结果 (UI 显示用，这里保持 ImageBitmap 即可，因为它是临时生成的预览)
    private val _binaryResultState = MutableStateFlow<ImageBitmap?>(null)
    val binaryResultState = _binaryResultState.asStateFlow()

    // 6. 内部状态：目标区域的原图 (用于计算二值化)
    private var _targetRegionRaw: BufferedImage? = null

    private val _screenshots = MutableStateFlow<List<ImageLayer>>(emptyList())
    val screenshots = _screenshots.asStateFlow()

    // 当前选中的 Tab 索引
    private val _selectedIndex = MutableStateFlow(0)
    val selectedIndex = _selectedIndex.asStateFlow()

    /**
     * 接收一张新截图 (通常由 ScreenCaptureService 调用)
     */
    fun addScreenshot(image: BufferedImage) {
        val newLayer = ImageLayer(
            id = UUID.randomUUID().toString(),
            name = "截图 ${System.currentTimeMillis() / 1000 % 10000}", // 简短命名
            image = image
        )

        val newList = _screenshots.value + newLayer
        _screenshots.value = newList

        // 自动选中刚截的那张
        selectScreenshot(newList.lastIndex)
    }

    /**
     * 切换选中的截图
     */
    fun selectScreenshot(index: Int) {
        val list = _screenshots.value
        if (index in list.indices) {
            _selectedIndex.value = index
            // [关键] 同步设置当前正在分析的图片 (setImage 是我们之前定义的)
            setImage(list[index])
        }
    }

    /**
     * 关闭截图 Tab
     */
    fun closeScreenshot(index: Int) {
        val currentList = _screenshots.value.toMutableList()
        if (index !in currentList.indices) return

        currentList.removeAt(index)
        _screenshots.value = currentList

        // 如果列表空了，清空当前显示
        if (currentList.isEmpty()) {
            setImage(null)
            _selectedIndex.value = -1
        } else {
            // 否则选中前一张或第一张
            val newIndex = (index - 1).coerceAtLeast(0)
            selectScreenshot(newIndex)
        }
    }

    // ============================================================================================
    // Actions (动作)
    // ============================================================================================

    fun activateTool(tool: PickingToolState) {
        _currentTool.value = tool
        if (tool is PickingToolState.None) {
            clearPreview()
        }
    }

    /**
     * 设置当前工作的图层
     * [修改点]：接收 ImageLayer
     */
    fun setImage(layer: ImageLayer?) {
        // 通过 ID 判断是否是同一张图，避免重复刷新
        if (_displayImage.value?.id != layer?.id) {
            _displayImage.value = layer

            // 切换图片时，重置会话状态
            _featurePoints.value = emptyList()
            _targetRegionRaw = null
            _binaryResultState.value = null
            clearPreview()
        }
    }

    /**
     * 设置二值化分析的目标区域
     */
    fun setTargetRegion(image: BufferedImage?) {
        _targetRegionRaw = image
        recalculateBinarization()
    }

    /**
     * 更新放大镜预览
     */
    fun updatePreview(x: Int, y: Int, color: Color, magnifier: ImageBitmap) {
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

    // ============================================================================================
    // Feature Point Management (特征点管理)
    // ============================================================================================

    fun addPoint(x: Int, y: Int, color: Color) {
        val currentList = _featurePoints.value
        val newIndex = currentList.size + 1

        val newPoint = FeaturePoint(
            index = newIndex,
            x = x,
            y = y,
            colorHex = color.toHexString(),
            tolerance = "101010",
            isChecked = true
        )

        _featurePoints.update { it + newPoint }
        recalculateBinarization()
    }

    fun removePoint(index: Int) {
        _featurePoints.update { list ->
            list.filter { it.index != index }
                .mapIndexed { i, point -> point.copy(index = i + 1) }
        }
        recalculateBinarization()
    }

    fun removePointById(id: String) {
        _featurePoints.update { list ->
            list.filter { it.id != id }
                .mapIndexed { i, point -> point.copy(index = i + 1) }
        }
        recalculateBinarization()
    }

    fun updatePoint(point: FeaturePoint) {
        _featurePoints.update { list ->
            list.map { if (it.id == point.id) point else it }
        }
        recalculateBinarization()
    }

    // ============================================================================================
    // Logic (二值化计算)
    // ============================================================================================

    private fun recalculateBinarization() {
        val rawImage = _targetRegionRaw ?: return
        val points = _featurePoints.value

        viewModelScope.launch(Dispatchers.Default) {
            val binaryBitmap = generateBinaryImage(rawImage, points)
            _binaryResultState.value = binaryBitmap
        }
    }

    private fun generateBinaryImage(image: BufferedImage, points: List<FeaturePoint>): ImageBitmap {
        val width = image.width
        val height = image.height
        val binaryImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

        val activePoints = points.filter { it.isChecked }

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = image.getRGB(x, y)
                val javaColor = java.awt.Color(pixel, true)
                val pixelColor = Color(javaColor.red, javaColor.green, javaColor.blue, javaColor.alpha)

                var isMatch = false

                if (activePoints.isEmpty()) {
                    isMatch = false
                } else {
                    for (point in activePoints) {
                        val targetColor = parseHexColor(point.colorHex)
                        if (ColorMatcher.isMatch(targetColor, pixelColor, point.tolerance)) {
                            isMatch = true
                            break
                        }
                    }
                }

                val resultColor = if (isMatch) java.awt.Color.WHITE.rgb else java.awt.Color.BLACK.rgb
                binaryImage.setRGB(x, y, resultColor)
            }
        }
        return binaryImage.toComposeImageBitmap()
    }

    private fun parseHexColor(hex: String): Color {
        return try {
            val cleanHex = hex.removePrefix("#")
            val rgb = cleanHex.toLong(16)
            val argb = if (cleanHex.length == 6) rgb or 0xFF000000 else rgb
            Color(argb)
        } catch (e: Exception) {
            Color.Black
        }
    }
}