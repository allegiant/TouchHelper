package org.eu.freex.tools.modules.image.presentation.features.editor.strategies

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import org.eu.freex.tools.common.model.PickingType
import java.awt.Cursor

/**
 * [PickingStrategy]
 * 全局取色/取点策略。
 * 优先级通常高于具体的 Tab 策略。
 */
class PickingStrategy(
    private val pickingType: PickingType,
    private val onPick: (Offset, Color) -> Unit
) : CanvasTabStrategy {

    override val showMagnifier: Boolean = true

    override fun getCursorIcon(): PointerIcon {
        return PointerIcon(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR))
    }

    override fun onTap(x: Int, y: Int, color: Color): Boolean {
        // 将像素坐标转回 Float Offset 传递给 VM
        onPick(Offset(x.toFloat(), y.toFloat()), color)
        return true
    }
}