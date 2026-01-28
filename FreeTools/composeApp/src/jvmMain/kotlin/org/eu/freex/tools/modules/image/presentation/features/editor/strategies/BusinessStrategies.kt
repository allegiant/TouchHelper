package org.eu.freex.tools.modules.image.presentation.features.editor.strategies

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import org.eu.freex.tools.modules.image.domain.model.FeaturePoint
import org.eu.freex.tools.modules.image.domain.model.SegmentationProject
import org.eu.freex.tools.modules.image.presentation.features.editor.components.MagnifierOverlay
import org.eu.freex.tools.modules.image.presentation.features.feature.components.drawFeaturePointsOverlay
import org.eu.freex.tools.modules.image.presentation.features.segmentation.components.drawSegmentationOverlay
import java.awt.Cursor
import java.awt.image.BufferedImage

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


    @Composable
    override fun HoverOverlay(
        modifier: Modifier,
        image: BufferedImage,
        screenPos: Offset,
        pixelPos: IntOffset,
        inBounds: Boolean
    ) {
        // 仅当在图片范围内时显示放大镜
        if (inBounds) {
            Box(modifier = Modifier.zIndex(200f)) {
                MagnifierOverlay(
                    sourceImage = image,
                    centerPixel = pixelPos,
                    screenPos = screenPos,
                    zoomLevel = 10,
                    gridSize = 15
                )
            }
        } else {
            // 超出范围时，回退到默认信息条，或者什么都不显示
            super.HoverOverlay(modifier, image, screenPos, pixelPos, false)
        }
    }}

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