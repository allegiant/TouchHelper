package org.eu.freex.tools.modules.image.presentation.features.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.runtime.*
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
 * 职责：
 * 1. 渲染底图并处理 Zoom/Pan 变换。
 * 2. 将交互事件 (Tap, Hover) 转换为像素坐标并回调。
 * 3. 提供 Slot 供外部注入业务渲染层。
 */
@Composable
fun EditorCanvasContent(
    modifier: Modifier = Modifier,
    displayImage: ImageLayer?,
    transformState: State<EditorCanvasTransform>,

    // === 1. 行为配置 (来自 Behavior) ===
    enableZoomPan: Boolean = true,
    cursorIcon: PointerIcon = PointerIcon.Default,

    // === 2. 渲染插槽 (Slots) ===
    /** * 在 Canvas 内部绘制 (内容会跟随图片缩放)
     * 典型用途：绘制特征点、分割 Mask、标注线
     * [textMeasurer]: 传入测量器以便在 Canvas 中绘制文字
     */
    drawOnImage: DrawScope.(TextMeasurer) -> Unit = {},

    /** * 在 Viewport 顶层绘制 (内容不跟随缩放，始终覆盖全屏)
     * 典型用途：框选组件、复杂的交互式 Composable UI
     */
    overlayContent: @Composable BoxScope.() -> Unit = {},

    /** * 自定义悬浮层逻辑
     * 如果不传 (null)，则使用默认的 [DefaultHoverInfoOverlay]
     */
    hoverContent: (@Composable BoxScope.(BufferedImage, Offset, IntOffset, Boolean) -> Unit)? = null,

    // === 3. 事件回调 ===
    onTap: (x: Int, y: Int, color: Color) -> Unit = { _, _, _ -> },
    onDoubleTap: (x: Int, y: Int) -> Unit = { _, _ -> },
    onTransform: (zoomChange: Float, panChange: Offset) -> Unit
) {
    // === 内部状态 ===
    // hoverPixelPos 存储的是相对于图片的像素坐标 (Local Coordinate)
    var hoverPixelPos by remember { mutableStateOf<Offset?>(null) }

    // 初始化文字测量器，供 Canvas 使用
    val textMeasurer = rememberTextMeasurer()

    val density = LocalDensity.current
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerLowest

    // === 手势处理 (Camera/Viewport 操作) ===
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
            .pointerHoverIcon(cursorIcon) // 应用光标样式
            .conditional(enableZoomPan) {
                transformable(state = transformableState)
            }
    ) {
        // 获取视口大小，用于后续计算 ScreenPos
        val viewportWidth = constraints.maxWidth.toFloat()
        val viewportHeight = constraints.maxHeight.toFloat()

        if (displayImage?.image != null) {
            val bufferedImage = displayImage.image
            val bitmap = remember(bufferedImage) { bufferedImage.toComposeImageBitmap() }
            val imageWidth = bufferedImage.width
            val imageHeight = bufferedImage.height

            // 1dp = 1px (通过 density 转换确保 Box 大小严格等于图片像素大小)
            val imageWidthDp = with(density) { imageWidth.toDp() }
            val imageHeightDp = with(density) { imageHeight.toDp() }

            // === 核心渲染容器 (Inner Box) ===
            // 这是一个 "1:1 像素映射" 的容器，它的 (0,0) 就是图片的 (0,0)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(imageWidthDp, imageHeightDp)
                    .graphicsLayer {
                        // 应用缩放和平移 (GPU 变换)
                        val transform = transformState.value
                        scaleX = transform.scale
                        scaleY = transform.scale
                        translationX = transform.pan.x
                        translationY = transform.pan.y
                    }
                    // [点击] 监听图片内的点击
                    .pointerInput(displayImage) {
                        detectTapGestures(
                            onTap = { localOffset ->
                                // localOffset 是相对于图片左上角的坐标 (Pixels)
                                val x = floor(localOffset.x).toInt()
                                val y = floor(localOffset.y).toInt()

                                // 边界检查
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
                    // [悬停] 监听图片内的鼠标移动
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull()
                                if (event.type == PointerEventType.Move && change != null) {
                                    // change.position 是 Local (Pixel) 坐标
                                    hoverPixelPos = change.position
                                } else if (event.type == PointerEventType.Exit) {
                                    hoverPixelPos = null
                                }
                            }
                        }
                    }
            ) {
                // 内部 Canvas，只负责绘制
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawImage(bitmap, filterQuality = FilterQuality.None)

                    // [Slot 1] 执行业务绘制 (传入 textMeasurer)
                    drawOnImage(textMeasurer)
                }
            }

            // [Slot 2] 执行顶层 Overlay (如框选框)
            overlayContent()

            // === 悬浮层 (Hover) 处理 ===
            if (hoverPixelPos != null) {
                val pixelX = floor(hoverPixelPos!!.x).toInt()
                val pixelY = floor(hoverPixelPos!!.y).toInt()
                val inBounds = pixelX in 0 until imageWidth && pixelY in 0 until imageHeight

                if (inBounds) {
                    val transform = transformState.value

                    // [反算 ScreenPos] 用于放置 Overlay Tooltip
                    // 公式：Screen = ViewportCenter + (Local - ImageCenter) * Scale + Pan
                    val screenX = (viewportWidth / 2f) + (hoverPixelPos!!.x - imageWidth / 2f) * transform.scale + transform.pan.x
                    val screenY = (viewportHeight / 2f) + (hoverPixelPos!!.y - imageHeight / 2f) * transform.scale + transform.pan.y

                    // [Slot 3] 执行 Hover UI
                    if (hoverContent != null) {
                        hoverContent(bufferedImage, Offset(screenX, screenY), IntOffset(pixelX, pixelY), inBounds)
                    } else {
                        // 默认实现
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

// 辅助函数：条件 Modifier
private fun Modifier.conditional(condition: Boolean, modifier: Modifier.() -> Modifier): Modifier {
    return if (condition) then(modifier(Modifier)) else this
}