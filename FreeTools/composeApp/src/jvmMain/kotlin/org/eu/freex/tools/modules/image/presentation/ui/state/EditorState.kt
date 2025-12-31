package org.eu.freex.tools.modules.image.presentation.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset

/**
 * 画布状态持有者 (State Holder)
 * 负责管理缩放、平移、悬停等纯 UI 交互状态
 */
@Stable
class EditorState(
    initialScale: Float = 1f,
    initialOffset: Offset = Offset.Zero
) {
    var mainScale by mutableFloatStateOf(initialScale)
    var mainOffset by mutableStateOf(initialOffset)

    // 悬停取色状态
    var hoverPixelPos by mutableStateOf<IntOffset?>(null)
    var hoverColor by mutableStateOf(Color.Transparent)

    fun zoom(zoomFactor: Float) {
        // 限制缩放范围 0.1 ~ 20 倍
        mainScale = (mainScale * zoomFactor).coerceIn(0.1f, 20f)
    }

    fun pan(dragAmount: Offset) {
        mainOffset += dragAmount
    }

    fun updateHover(pos: IntOffset?, color: Color) {
        hoverPixelPos = pos
        hoverColor = color
    }

    fun reset(targetScale: Float = 1f, targetOffset: Offset = Offset.Zero) {
        mainScale = targetScale
        mainOffset = targetOffset
    }
}

@Composable
fun rememberEditorState(
    initialScale: Float = 1f,
    initialOffset: Offset = Offset.Zero
) = remember {
    EditorState(initialScale, initialOffset)
}