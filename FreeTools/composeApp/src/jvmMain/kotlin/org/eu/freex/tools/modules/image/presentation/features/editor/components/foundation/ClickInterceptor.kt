package org.eu.freex.tools.modules.image.presentation.features.editor.components.foundation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures

/**
 * [ClickInterceptor]
 * 一个透明的点击拦截层。
 * 用于在 "抓抓模式" 下独占点击事件，防止误触底下的业务组件。
 */
@Composable
fun ClickInterceptor(
    modifier: Modifier = Modifier,
    cursorIcon: PointerIcon = PointerIcon.Crosshair, // 默认十字准星
    onHover: (Offset) -> Unit = {},
    onClick: (Offset) -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerHoverIcon(cursorIcon)
            .pointerInput(Unit) {
                // 1. 监听悬浮 (用于更新放大镜位置)
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull()
                        if (change != null) {
                            onHover(change.position)
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                // 2. 拦截点击
                detectTapGestures { offset ->
                    onClick(offset)
                }
            }
    )
}