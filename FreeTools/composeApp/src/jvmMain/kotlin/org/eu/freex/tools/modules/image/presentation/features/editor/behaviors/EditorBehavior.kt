package org.eu.freex.tools.modules.image.presentation.features.editor.behaviors

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.IntRect
import org.eu.freex.tools.common.model.PickingType
import org.eu.freex.tools.common.utils.toComposeColor
import org.eu.freex.tools.modules.image.domain.model.FeaturePoint
import org.eu.freex.tools.modules.image.domain.model.SegmentationProject
import org.eu.freex.tools.modules.image.presentation.features.segmentation.components.drawSegmentationOverlay
import java.awt.Cursor

/**
 * [EditorBehavior]
 * 状态与行为的集合体。
 */
sealed interface EditorBehavior {
    val cursor: PointerIcon get() = PointerIcon.Default
    val enableZoomPan: Boolean get() = true

    /**
     * 处理画布点击 (逻辑层)
     */
    fun onTap(x: Int, y: Int, color: Color) {}

    /**
     * 处理画布双击 (逻辑层)
     */
    fun onDoubleTap(x: Int, y: Int) {}

    /**
     * 画布内部绘制逻辑 (渲染层 - 跟随缩放)
     * 必须传入 [textMeasurer] 因为很多绘制逻辑(如分割序号)需要测量文字
     */
    fun DrawScope.onDraw(textMeasurer: TextMeasurer) {}
}

// === 1. 默认状态 ===
data object DefaultBehavior : EditorBehavior

// === 2. 框选模式 (对应 RegionSelectorOverlay) ===
data class RegionSelectingBehavior(
    val onEnd: (IntRect) -> Unit,
    val onCancel: () -> Unit
) : EditorBehavior {
    // 框选时显示十字光标，且禁止底图拖拽
    override val cursor: PointerIcon = PointerIcon.Crosshair
    override val enableZoomPan: Boolean = false
}

// === 3. 取色/取点模式 (对应 MagnifierOverlay) ===
data class PickingBehavior(
    val type: PickingType,
    val onPick: (Int, Int, Color) -> Unit
) : EditorBehavior {
    // 使用十字准星
    override val cursor: PointerIcon = PointerIcon(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR))

    override fun onTap(x: Int, y: Int, color: Color) {
        onPick(x, y, color)
    }
}

// === 4. 特征点模式 (绘制圆点) ===
data class FeatureBehavior(
    val points: List<FeaturePoint>,
    val onAddPoint: (Int, Int, Color) -> Unit
) : EditorBehavior {

    override fun onTap(x: Int, y: Int, color: Color) {
        onAddPoint(x, y, color)
    }

    override fun DrawScope.onDraw(textMeasurer: TextMeasurer) {
        // 直接实现简单的特征点绘制，或者调用 FeatureOverlay 如果有的话
        points.forEach { point ->
            val color = point.colorHex.toComposeColor()
            // 绘制实心点
            drawCircle(
                color = color,
                radius = 10f, // 10px 半径
                center = Offset(point.x.toFloat(), point.y.toFloat())
            )
            // 绘制白色描边确保深色背景可见
            drawCircle(
                color = Color.White,
                radius = 10f,
                center = Offset(point.x.toFloat(), point.y.toFloat()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
            )
        }
    }
}

// === 5. 分割识别模式 (对应 SegmentationOverlay) ===
data class SegmentationBehavior(
    val project: SegmentationProject?,
    val selectedIndex: Int,
    val onSelect: (Int, Int) -> Unit,
    val onShowDialog: () -> Unit
) : EditorBehavior {

    override fun onTap(x: Int, y: Int, color: Color) {
        onSelect(x, y)
    }

    override fun onDoubleTap(x: Int, y: Int) {
        onShowDialog()
    }

    override fun DrawScope.onDraw(textMeasurer: TextMeasurer) {
        // 调用你上传文件中的扩展函数
        drawSegmentationOverlay(
            project = project,
            textMeasurer = textMeasurer,
            selectedIndex = selectedIndex
        )
    }
}

// [新增] 6. 滤镜/通用模式 (对应旧的 FilterStrategy)
// 允许缩放，且支持点击取色
data class FilterBehavior(
    val onTapAction: (Int, Int, Color) -> Unit
) : EditorBehavior {
    override fun onTap(x: Int, y: Int, color: Color) {
        onTapAction(x, y, color)
    }
}