package org.eu.freex.tools.modules.image.presentation.features.editor.components.foundation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import org.eu.freex.tools.modules.image.domain.model.ImageLayer
import org.eu.freex.tools.modules.image.presentation.viewmodel.EditorCanvasTransform

@Composable
fun EditorCanvasContainer(
    modifier: Modifier = Modifier,
    displayImage: ImageLayer?,
    transformState: EditorCanvasTransform,
    cursorIcon: PointerIcon = PointerIcon.Default,
    enablePan: Boolean = true, // 控制是否允许拖拽平移。默认为 true
    onTransform: (zoomChange: Float, panChange: Offset) -> Unit,
    onHover: (Offset?) -> Unit = {}, // [新增] 接收 Hover 回调
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerLowest

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        onTransform(zoomChange, panChange)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .clipToBounds()
            .pointerHoverIcon(cursorIcon) // 光标设置在最外层，确保生效
            .pointerInput(Unit) {
                // 滚轮缩放逻辑
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val change = event.changes.last()
                            val scrollDelta = change.scrollDelta.y
                            val zoomFactor = if (scrollDelta > 0) 0.9f else 1.1f
                            onTransform(zoomFactor, Offset.Zero)
                            change.consume()
                        }
                    }
                }
            }
            .transformable(state = transformableState)
            .pointerInput(enablePan) {
                detectDragGestures { _, dragAmount ->
                    onTransform(1f, dragAmount)
                }
            }
    ) {
        if (displayImage?.image != null) {
            val bufferedImage = displayImage.image
            val bitmap = remember(bufferedImage) { bufferedImage.toComposeImageBitmap() }
            val imageWidth = bufferedImage.width
            val imageHeight = bufferedImage.height

            val imageWidthDp = with(density) { imageWidth.toDp() }
            val imageHeightDp = with(density) { imageHeight.toDp() }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .requiredSize(imageWidthDp, imageHeightDp)
                    .graphicsLayer {
                        scaleX = transformState.scale
                        scaleY = transformState.scale
                        translationX = transformState.pan.x
                        translationY = transformState.pan.y
                    }
                    // [核心修复] 在这里监听移动。
                    // 1. 它是 Parent，所以即便子元素(业务图层)填充了满屏，这里也能收到事件。
                    // 2. 使用 Main Pass 即可，因为通常业务层的 detectTapGestures 不会消费 Move 事件。
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.firstOrNull()
                                if (event.type == PointerEventType.Move && change != null) {
                                    // 这里的 change.position 就是相对于 Image 左上角的像素坐标
                                    onHover(change.position)
                                } else if (event.type == PointerEventType.Exit) {
                                    onHover(null)
                                }
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawImage(bitmap, filterQuality = FilterQuality.None)
                }
                content()
            }
        } else {
            Text(
                "请导入图片",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}