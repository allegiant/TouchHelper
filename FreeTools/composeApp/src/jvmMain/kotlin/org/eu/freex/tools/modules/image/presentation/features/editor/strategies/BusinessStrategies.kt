package org.eu.freex.tools.modules.image.presentation.features.editor.strategies

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.text.TextMeasurer
import org.eu.freex.tools.modules.image.domain.model.FeaturePoint
import org.eu.freex.tools.modules.image.domain.model.SegmentationProject
import org.eu.freex.tools.modules.image.presentation.features.feature.components.drawFeaturePointsOverlay
import org.eu.freex.tools.modules.image.presentation.features.segmentation.components.drawSegmentationOverlay
import java.awt.Cursor

// === 1. 滤镜策略 (Filter) ===
class FilterStrategy(
    // 滤镜模式下点击通常也用于取色，或者无操作
    private val onClick: (Int, Int, Color) -> Unit
) : CanvasTabStrategy {
    override fun onTap(x: Int, y: Int, color: Color): Boolean {
        onClick(x, y, color)
        return true
    }
}

// === 2. 特征点策略 (Feature) ===
class FeatureStrategy(
    private val points: List<FeaturePoint>,
    private val onAddPoint: (Int, Int, Color) -> Unit
) : CanvasTabStrategy {

    // 特征点模式总是开启放大镜，方便精准点击
    override val showMagnifier: Boolean = true

    override fun getCursorIcon(): PointerIcon {
        return PointerIcon(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR))
    }

    override fun DrawScope.drawOverlay(textMeasurer: TextMeasurer) {
        // 调用您现有的绘制函数
        drawFeaturePointsOverlay(points, textMeasurer)
    }

    override fun onTap(x: Int, y: Int, color: Color): Boolean {
        onAddPoint(x, y, color)
        return true
    }
}

// === 3. 切割策略 (Segmentation) ===
class SegmentationStrategy(
    private val project: SegmentationProject?,
    private val selectedIndex: Int,
    private val onSelect: (Int, Int) -> Unit,
    private val onDoubleTapAction: () -> Unit
) : CanvasTabStrategy {

    override fun getCursorIcon(): PointerIcon {
        return PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
    }

    override fun DrawScope.drawOverlay(textMeasurer: TextMeasurer) {
        if (project != null) {
            // 调用您现有的绘制函数
            drawSegmentationOverlay(project, textMeasurer, selectedIndex)
        }
    }

    override fun onTap(x: Int, y: Int, color: Color): Boolean {
        onSelect(x, y)
        return true
    }

    override fun onDoubleTap(x: Int, y: Int) {
        // 双击逻辑：先选中，再执行动作（如弹窗）
        onSelect(x, y)
        onDoubleTapAction()
    }
}