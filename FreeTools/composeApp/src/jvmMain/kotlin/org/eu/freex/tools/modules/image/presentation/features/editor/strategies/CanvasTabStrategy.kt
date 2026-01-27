package org.eu.freex.tools.modules.image.presentation.features.editor.strategies

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.text.TextMeasurer
import java.awt.Cursor

/**
 * [CanvasTabStrategy]
 * 定义画布在不同业务模式下的行为契约。
 */
interface CanvasTabStrategy {
    // === 1. 状态标志 ===
    /** 是否允许底图缩放和平移。如果为 false，通常意味着策略接管了拖拽手势 (如框选)。 */
    val enableZoomPan: Boolean get() = true
    /** 是否显示放大镜悬浮窗 */
    val showMagnifier: Boolean get() = false

    // === 2. 视觉呈现 ===
    fun getCursorIcon(): PointerIcon = PointerIcon.Default

    /**
     * 绘制业务层 Overlay (运行在 Image 坐标系中)。
     * 在这里绘制的内容会自动跟随底图缩放和平移。
     */
    fun DrawScope.drawOverlay(textMeasurer: TextMeasurer) {}

    /**
     * 顶层 Composable 覆盖层 (运行在 Screen 坐标系中)。
     * 用于放置原生组件、复杂的交互式 Overlay (如 RegionSelector)。
     */
    @Composable
    fun ContentOverlay(modifier: Modifier) {}

    // === 3. 交互事件 (返回 true 表示消费事件) ===
    fun onTap(x: Int, y: Int, color: Color): Boolean = false
    fun onDoubleTap(x: Int, y: Int) {}

    // 拖拽手势 (仅当 enableZoomPan = false 时生效，或由具体实现决定如何处理)
    fun onDragStart(start: Offset) {}
    fun onDrag(dragAmount: Offset) {}
    fun onDragEnd() {}
}