package org.eu.freex.tools.modules.image.presentation.features.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.eu.freex.tools.common.utils.ImageUtils
import org.eu.freex.tools.modules.image.domain.model.ImageLayer
import org.eu.freex.tools.modules.image.presentation.features.editor.strategies.CanvasTabStrategy
import org.eu.freex.tools.modules.image.presentation.viewmodel.EditorCanvasTransform
import kotlin.math.floor

/**
 * 核心渲染组件 (Dumb Component)。
 */
@Composable
fun EditorCanvasContent(
    modifier: Modifier = Modifier,
    displayImage: ImageLayer?,
    strategy: CanvasTabStrategy,
    transformState: State<EditorCanvasTransform>,
    onTransform: (zoomChange: Float, panChange: Offset) -> Unit
) {
    // === 1. 画布状态 ===
    // hoverPixelPos 存储的是相对于图片的像素坐标 (Local Coordinate)
    var hoverPixelPos by remember { mutableStateOf<Offset?>(null) }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // === 2. 手势处理 (Camera/Viewport 操作) ===
    // 缩放和平移是针对"视口"的操作，所以保留在最外层
    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        if (strategy.enableZoomPan) {
            onTransform(zoomChange, panChange)
        }
    }

    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerLowest

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .clipToBounds()
            .pointerHoverIcon(strategy.getCursorIcon())
            // 视口手势 (Pan/Zoom)
            .conditional(strategy.enableZoomPan) {
                transformable(state = transformableState)
            }
        // [新增] 背景点击 (可选：如果需要在图片外点击取消选中，可以在这里处理)
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

            // === 3. 渲染容器 (Inner Box) ===
            // 这是一个 "1:1 像素映射" 的容器。
            // 它的 (0,0) 就是图片的 (0,0)。
            // 我们对它应用 graphicsLayer 进行视觉变换，但 pointerInput 会自动处理逆变换。
            Box(
                modifier = Modifier
                    .align(Alignment.Center) // 布局上居中
                    .size(imageWidthDp, imageHeightDp) // 尺寸严格匹配像素
                    .graphicsLayer {
                        // 视觉变换 (GPU)
                        val transform = transformState.value
                        scaleX = transform.scale
                        scaleY = transform.scale
                        translationX = transform.pan.x
                        translationY = transform.pan.y
                        // transformOrigin 默认为 Center，即 Box 中心
                    }
                    // 将 Tap 和 Hover 监听移到这里！
                    // Compose 会自动将屏幕坐标转换为这个 Box 的 Local 坐标。
                    // 这里的 offset.x 几乎等于 pixelX。
                    .pointerInput(displayImage, strategy) {
                        detectTapGestures(
                            onTap = { localOffset ->
                                // localOffset 是相对于图片左上角的坐标 (Pixels)
                                val x = floor(localOffset.x).toInt()
                                val y = floor(localOffset.y).toInt()

                                // 边界检查
                                if (x in 0 until imageWidth && y in 0 until imageHeight) {
                                    val color = ImageUtils.getPixelColor(bufferedImage, x, y)
                                    strategy.onTap(x, y, color)
                                }
                            },
                            onDoubleTap = { localOffset ->
                                val x = floor(localOffset.x).toInt()
                                val y = floor(localOffset.y).toInt()
                                if (x in 0 until imageWidth && y in 0 until imageHeight) {
                                    strategy.onDoubleTap(x, y)
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
                    // Strategy Overlay 也是在 Image 坐标系中绘制
                    with(strategy) {
                        drawOverlay(textMeasurer)
                    }
                }
            }

            // === 4. Overlay & Hover ===
            strategy.ContentOverlay(modifier = Modifier.fillMaxSize())

            // 计算悬浮层位置
            if (hoverPixelPos != null) {
                val pixelX = floor(hoverPixelPos!!.x).toInt()
                val pixelY = floor(hoverPixelPos!!.y).toInt()
                val inBounds = pixelX in 0 until imageWidth && pixelY in 0 until imageHeight

                if (inBounds) {
                    val transform = transformState.value

                    // [反算 ScreenPos] 用于放置 Overlay Tooltip
                    // 公式：Screen = ViewportCenter + (Local - ImageCenter) * Scale + Pan
                    val screenX =
                        (viewportWidth / 2f) + (hoverPixelPos!!.x - imageWidth / 2f) * transform.scale + transform.pan.x
                    val screenY =
                        (viewportHeight / 2f) + (hoverPixelPos!!.y - imageHeight / 2f) * transform.scale + transform.pan.y

                    strategy.HoverOverlay(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp)
                            .zIndex(190f),
                        image = bufferedImage,
                        screenPos = Offset(screenX, screenY), // 用于 Magnifier 显示在鼠标位置
                        pixelPos = IntOffset(pixelX, pixelY), // 用于显示 RGB/坐标数值
                        inBounds = inBounds
                    )
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

fun Modifier.conditional(condition: Boolean, modifier: Modifier.() -> Modifier): Modifier {
    return if (condition) then(modifier(Modifier)) else this
}