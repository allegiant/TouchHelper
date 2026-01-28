package org.eu.freex.tools.modules.image.presentation.features.editor.strategies

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import org.eu.freex.tools.common.model.PickingType
import org.eu.freex.tools.modules.image.presentation.features.editor.components.MagnifierOverlay
import java.awt.Cursor
import java.awt.image.BufferedImage

/**
 * [PickingStrategy]
 * 全局取色/取点策略。
 * 优先级通常高于具体的 Tab 策略。
 */
class PickingStrategy(
    private val pickingType: PickingType,
    private val onPick: (Offset, Color) -> Unit
) : CanvasTabStrategy {

    override fun getCursorIcon(): PointerIcon {
        return PointerIcon(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR))
    }

    override fun onTap(x: Int, y: Int, color: Color): Boolean {
        // 将像素坐标转回 Float Offset 传递给 VM
        onPick(Offset(x.toFloat(), y.toFloat()), color)
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
    }
}