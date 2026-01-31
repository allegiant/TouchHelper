package org.eu.freex.tools.modules.image.presentation.features.editor

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.unit.IntSize
import org.eu.freex.tools.modules.image.presentation.features.editor.components.foundation.EditorCanvasContainer
import org.eu.freex.tools.modules.image.presentation.viewmodel.EditorCanvasViewModel
import org.koin.compose.koinInject

/**
 * [EditorCanvasPanel]
 * 纯净的编辑器画布容器。
 * 它不包含任何特定工具逻辑，只负责：
 * 1. 渲染底图
 * 2. 处理缩放/平移
 * 3. 提供 Content (跟随缩放) 和 Overlay (固定悬浮) 两个插槽
 */
@Composable
fun EditorCanvasPanel(
    modifier: Modifier = Modifier,
    editorViewModel: EditorCanvasViewModel = koinInject(),
    cursorIcon: PointerIcon = PointerIcon.Default,
    // [插槽 1] 内部层
    content: @Composable (BoxScope.() -> Unit) = {},
    // [插槽 2] 外部层 (升级：传入 ViewportSize 和 HoverPos)
    overlay: @Composable (BoxScope.(IntSize, Offset?) -> Unit) = { _, _ -> }
) {
    val uiState by editorViewModel.uiState.collectAsState()
    val transformState by editorViewModel.transformState.collectAsState()

    var hoverPixelPos by remember { mutableStateOf<Offset?>(null) }

    BoxWithConstraints(modifier = modifier) {
        val viewportSize = IntSize(constraints.maxWidth, constraints.maxHeight)

        EditorCanvasContainer(
            displayImage = uiState.displayImage,
            transformState = transformState,
            cursorIcon = cursorIcon,
            onTransform = { zoom, pan -> editorViewModel.updateTransform(zoom, pan) },
            onHover = { hoverPixelPos = it } // 捕获鼠标位置

        ) {
            if (uiState.displayImage?.image != null) {
                // 渲染内部跟随缩放的内容
                content()
            }
        }

        // 渲染外部固定悬浮的内容
        if (uiState.displayImage?.image != null) {
            overlay(viewportSize,hoverPixelPos)
        }
    }
}