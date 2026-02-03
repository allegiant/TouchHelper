/* Path: .../modules/image/presentation/viewmodel/PickingToolViewModel.kt */
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

// [新增] 查找方向枚举
enum class FindDirection(val label: String, val code: Int) {
    LEFT_TOP_RIGHT_BOTTOM("左上 -> 右下", 0),
    RIGHT_TOP_LEFT_BOTTOM("右上 -> 左下", 1),
    LEFT_BOTTOM_RIGHT_TOP("左下 -> 右上", 2),
    RIGHT_BOTTOM_LEFT_TOP("右下 -> 左上", 3),
    CENTER_OUT("中心 -> 四周", 4);
}

class PickingToolViewModel : ViewModel() {

    // ============================================================================================
    // State (原有状态)
    // ============================================================================================

    private val _currentTool = MutableStateFlow<PickingToolState>(PickingToolState.None)
    val currentTool = _currentTool.asStateFlow()

    private val _displayImage = MutableStateFlow<ImageLayer?>(null)
    val displayImage = _displayImage.asStateFlow()

    private val _featurePoints = MutableStateFlow<List<FeaturePoint>>(emptyList())
    val featurePoints = _featurePoints.asStateFlow()

    private val _previewState = MutableStateFlow(PreviewState())
    val previewState = _previewState.asStateFlow()

    private val _binaryResultState = MutableStateFlow<ImageBitmap?>(null)
    val binaryResultState = _binaryResultState.asStateFlow()

    private var _targetRegionRaw: BufferedImage? = null

    private val _screenshots = MutableStateFlow<List<ImageLayer>>(emptyList())
    val screenshots = _screenshots.asStateFlow()

    private val _selectedIndex = MutableStateFlow(0)
    val selectedIndex = _selectedIndex.asStateFlow()

    // ============================================================================================
    // [新增] 找色配置参数
    // ============================================================================================

    // 1. 全局相似度 (默认 0.9)
    private val _globalSimilarity = MutableStateFlow(0.9f)
    val globalSimilarity = _globalSimilarity.asStateFlow()

    // 2. 查找方向
    private val _searchDirection = MutableStateFlow(FindDirection.LEFT_TOP_RIGHT_BOTTOM)
    val searchDirection = _searchDirection.asStateFlow()

    // 配置更新方法
    fun updateSimilarity(value: Float) { _globalSimilarity.value = value }
    fun updateDirection(direction: FindDirection) { _searchDirection.value = direction }

    // ============================================================================================
    // 截图管理 (保持不变)
    // ============================================================================================

    fun addScreenshot(image: BufferedImage) {
        val newLayer = ImageLayer(
            id = UUID.randomUUID().toString(),
            name = "截图 ${System.currentTimeMillis() / 1000 % 10000}",
            image = image
        )
        val newList = _screenshots.value + newLayer
        _screenshots.value = newList
        selectScreenshot(newList.lastIndex)
    }

    fun selectScreenshot(index: Int) {
        val list = _screenshots.value
        if (index in list.indices) {
            _selectedIndex.value = index
            setImage(list[index])
        }
    }

    fun closeScreenshot(layer: ImageLayer) {
        val currentList = _screenshots.value
        val index = currentList.indexOfFirst { it.id == layer.id }
        if (index != -1) {
            closeScreenshot(index)
        }
    }

    fun closeScreenshot(index: Int) {
        val currentList = _screenshots.value.toMutableList()
        if (index !in currentList.indices) return

        currentList.removeAt(index)
        _screenshots.value = currentList

        if (currentList.isEmpty()) {
            setImage(null)
            _selectedIndex.value = -1
        } else {
            val currentIndex = _selectedIndex.value
            if (index == currentIndex) {
                val newIndex = (index - 1).coerceAtLeast(0)
                selectScreenshot(newIndex)
            } else if (index < currentIndex) {
                _selectedIndex.value = currentIndex - 1
            }
        }
    }

    // ============================================================================================
    // Actions & Logic (保持不变)
    // ============================================================================================

    fun activateTool(tool: PickingToolState) {
        _currentTool.value = tool
        if (tool is PickingToolState.None) {
            clearPreview()
        }
    }

    fun setImage(layer: ImageLayer?) {
        if (_displayImage.value?.id != layer?.id) {
            _displayImage.value = layer
            _featurePoints.value = emptyList()
            _targetRegionRaw = null
            _binaryResultState.value = null
            clearPreview()
        }
    }

    fun setTargetRegion(image: BufferedImage?) {
        _targetRegionRaw = image
        recalculateBinarization()
    }

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

    fun addPoint(x: Int, y: Int, color: Color) {
        val currentList = _featurePoints.value
        val newIndex = currentList.size + 1
        val newPoint = FeaturePoint(
            index = newIndex,
            x = x,
            y = y,
            colorHex = color.toHexString(),
            tolerance = "101010", // 默认偏色
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
                if (activePoints.isNotEmpty()) {
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

    // ============================================================================================
    // [新增] 脚本生成逻辑
    // ============================================================================================

    fun generateScript(): String {
        val points = _featurePoints.value.filter { it.isChecked }
        if (points.isEmpty()) return "// 错误：请先在图片上点击取几个点"

        val sb = StringBuilder()
        val sim = _globalSimilarity.value
        val dir = _searchDirection.value.code
        val first = points.first()

        sb.append("// 多点找色：相似度 $sim, 方向 ${_searchDirection.value.label}\n")
        sb.append("var points = [\n")

        points.forEachIndexed { index, p ->
            val hex = if(p.colorHex.startsWith("#")) p.colorHex else "#${p.colorHex}"
            if (index == 0) {
                // 第一个点是主色: ["#RRGGBB", x, y]
                sb.append("    [\"$hex\", ${p.x}, ${p.y}], // 主色点\n")
            } else {
                // 后续点是相对坐标: ["#RRGGBB", offsetX, offsetY]
                val offsetX = p.x - first.x
                val offsetY = p.y - first.y
                sb.append("    [\"$hex\", $offsetX, $offsetY], // 第${index+1}点\n")
            }
        }
        sb.append("];\n\n")

        sb.append("var pos = findMultiColor(points, $sim, $dir);\n")
        sb.append("if (pos) {\n")
        sb.append("    console.log(\"找到了: \" + pos.x + \",\" + pos.y);\n")
        sb.append("    click(pos.x, pos.y);\n")
        sb.append("} else {\n")
        sb.append("    console.log(\"没找到\");\n")
        sb.append("}")

        return sb.toString()
    }
}