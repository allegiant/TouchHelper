/* file: EditorCanvasContent.kt */
package org.eu.freex.tools.modules.image.presentation.features.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.eu.freex.tools.common.utils.ImageUtils
import org.eu.freex.tools.modules.image.domain.model.ImageLayer
import org.eu.freex.tools.modules.image.presentation.features.editor.components.DefaultHoverInfoOverlay
import org.eu.freex.tools.modules.image.presentation.viewmodel.EditorCanvasTransform
import java.awt.image.BufferedImage
import kotlin.math.floor

/**
 * [EditorCanvasContent] (Dumb Component)
 * 修复版：
 * 1. 移除了错误的密度计算，修复放大镜偏移和取点坐标问题。
 * 2. 保留了 Desktop 端鼠标滚轮缩放和左键拖拽平移。
 */
@Composable
fun EditorCanvasContent(
    modifier: Modifier = Modifier,
    displayImage: ImageLayer?,
    transformState: State<EditorCanvasTransform>,

    // === 1. 行为配置 ===
    enableZoomPan: Boolean = true,
    cursorIcon: PointerIcon = PointerIcon.Default,

    // === 2. 渲染插槽 ===
    drawOnImage: DrawScope.(TextMeasurer) -> Unit = {},
    overlayContent: @Composable BoxScope.() -> Unit = {},
    hoverContent: (@Composable BoxScope.(BufferedImage, Offset, IntOffset, Boolean) -> Unit)? = null,

    // === 3. 事件回调 ===
    onTap: (x: Int, y: Int, color: Color) -> Unit = { _, _, _ -> },
    onDoubleTap: (x: Int, y: Int) -> Unit = { _, _ -> },
    onTransform: (zoomChange: Float, panChange: Offset) -> Unit
) {
    // === 内部状态 ===
    var hoverPixelPos by remember { mutableStateOf<Offset?>(null) }

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerLowest

    // === 手势处理 ===
    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        if (enableZoomPan) {
            onTransform(zoomChange, panChange)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .clipToBounds()
            .pointerHoverIcon(cursorIcon)
            // 1. 鼠标滚轮缩放支持 (Desktop)
            .pointerInput(enableZoomPan) {
                if (enableZoomPan) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Scroll) {
                                val change = event.changes.last()
                                val scrollDelta = change.scrollDelta.y
                                // 向下滚缩小，向上滚放大
                                val zoomFactor = if (scrollDelta > 0) 0.9f else 1.1f
                                onTransform(zoomFactor, Offset.Zero)
                                change.consume()
                            }
                        }
                    }
                }
            }
            .conditional(enableZoomPan) {
                transformable(state = transformableState)
            }
            // 2. 鼠标左键拖拽支持 (Desktop)
            .pointerInput(enableZoomPan) {
                if (enableZoomPan) {
                    detectDragGestures { _, dragAmount ->
                        onTransform(1f, dragAmount)
                    }
                }
            }
    ) {
        val viewportWidth = constraints.maxWidth.toFloat()
        val viewportHeight = constraints.maxHeight.toFloat()

        if (displayImage?.image != null) {
            val bufferedImage = displayImage.image
            val bitmap = remember(bufferedImage) { bufferedImage.toComposeImageBitmap() }
            val imageWidth = bufferedImage.width
            val imageHeight = bufferedImage.height

            // 关键点：使用 density 转换，确保 Box 的逻辑大小(DP) 等于 图片的像素大小(PX)
            // 这样 PointerInput 里的 1 unit 就严格对应 1 pixel
            val imageWidthDp = with(density) { imageWidth.toDp() }
            val imageHeightDp = with(density) { imageHeight.toDp() }

            // === 核心渲染容器 (Inner Box) ===
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(imageWidthDp, imageHeightDp)
                    .graphicsLayer {
                        val transform = transformState.value
                        scaleX = transform.scale
                        scaleY = transform.scale
                        translationX = transform.pan.x
                        translationY = transform.pan.y
                    }
                    // [点击]
                    .pointerInput(displayImage) {
                        detectTapGestures(
                            onTap = { localOffset ->
                                // [修复] 直接使用 floor，不除以 density
                                // 因为 Box 大小是 imageWidth.toDp()，所以 localOffset 范围就是 0..imageWidth
                                val x = floor(localOffset.x).toInt()
                                val y = floor(localOffset.y).toInt()

                                if (x in 0 until imageWidth && y in 0 until imageHeight) {
                                    val color = ImageUtils.getPixelColor(bufferedImage, x, y)
                                    onTap(x, y, color)
                                }
                            },
                            onDoubleTap = { localOffset ->
                                val x = floor(localOffset.x).toInt()
                                val y = floor(localOffset.y).toInt()
                                if (x in 0 until imageWidth && y in 0 until imageHeight) {
                                    onDoubleTap(x, y)
                                }
                            }
                        )
                    }
                    // [悬停]
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull()
                                if (event.type == PointerEventType.Move && change != null) {
                                    // [修复] 直接使用 position
                                    hoverPixelPos = change.position
                                } else if (event.type == PointerEventType.Exit) {
                                    hoverPixelPos = null
                                }
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawImage(bitmap, filterQuality = FilterQuality.None)
                    drawOnImage(textMeasurer)
                }
            }

            // [Overlay 插槽]
            overlayContent()

            // === 悬浮层 (Hover) ===
            if (hoverPixelPos != null) {
                val pixelX = floor(hoverPixelPos!!.x).toInt()
                val pixelY = floor(hoverPixelPos!!.y).toInt()
                val inBounds = pixelX in 0 until imageWidth && pixelY in 0 until imageHeight

                if (inBounds) {
                    val transform = transformState.value

                    // [修复] 计算 ScreenPos (用于 Overlay 定位)
                    // 这里的计算全部在 Logical Pixels (DP) 空间进行，不需要乘 density
                    // 公式：Center + (LocalPos - ImageCenter) * Scale + Pan

                    val imageCenterX = imageWidth / 2f // Logical center (since width=widthDp)
                    val imageCenterY = imageHeight / 2f

                    val screenX = (viewportWidth / 2f) + (hoverPixelPos!!.x - imageCenterX) * transform.scale + transform.pan.x
                    val screenY = (viewportHeight / 2f) + (hoverPixelPos!!.y - imageCenterY) * transform.scale + transform.pan.y

                    if (hoverContent != null) {
                        hoverContent(bufferedImage, Offset(screenX, screenY), IntOffset(pixelX, pixelY), inBounds)
                    } else {
                        DefaultHoverInfoOverlay(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(10.dp)
                                .zIndex(190f),
                            image = bufferedImage,
                            pixelPos = IntOffset(pixelX, pixelY),
                            inBounds = inBounds
                        )
                    }
                }
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

private fun Modifier.conditional(condition: Boolean, modifier: Modifier.() -> Modifier): Modifier {
    return if (condition) then(modifier(Modifier)) else this
}