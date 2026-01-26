package org.eu.freex.tools.modules.image.presentation.features.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex // [关键] 引入 zIndex
import java.awt.Cursor
import org.eu.freex.tools.modules.image.domain.model.ImageLayer
import org.eu.freex.tools.modules.image.presentation.features.editor.utils.CanvasUtils
import org.eu.freex.tools.modules.image.presentation.features.editor.components.MagnifierOverlay
import org.eu.freex.tools.modules.image.presentation.features.feature.components.RegionSelectorOverlay
import org.eu.freex.tools.modules.image.presentation.viewmodel.IntRect
import kotlin.math.abs
import kotlin.math.min

data class CanvasTapEvent(
    val screenPos: Offset,
    val pixelPos: IntOffset,
    val color: Color,
    val isToBounds: Boolean
)

@Composable
fun EditorCanvasPanel(
    modifier: Modifier = Modifier,
    displayImage: ImageLayer?,
    cursorIcon: PointerIcon = PointerIcon(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)),
    showMagnifier: Boolean = false,
    isSelectingRegion: Boolean = false,
    onRegionSelected: (IntRect) -> Unit = {},
    onDrawOverlay: DrawScope.() -> Unit = {},
    onCanvasTap: (CanvasTapEvent) -> Unit = {},
    onCanvasDoubleTap: (CanvasTapEvent) -> Unit = {}
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    var hoverPos by remember { mutableStateOf<Offset?>(null) }

    val state = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.1f, 10f)
        offset += panChange
    }

    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerLowest
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .clipToBounds()
            .pointerHoverIcon(cursorIcon)
            .transformable(state = state)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        if (event.type == PointerEventType.Move) {
                            hoverPos = change.position
                        } else if (event.type == PointerEventType.Exit) {
                            hoverPos = null
                        }
                    }
                }
            }
            .pointerInput(displayImage) {
                detectTapGestures(
                    onTap = { screenPos ->
                        val event = calculateTapEvent(screenPos, displayImage, size, scale, offset)
                        if (event != null) onCanvasTap(event)
                    },
                    onDoubleTap = { screenPos ->
                        val event = calculateTapEvent(screenPos, displayImage, size, scale, offset)
                        if (event != null) onCanvasDoubleTap(event)
                    }
                )
            }
    ) {
        val canvasSize = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val bufferedImage = displayImage?.image

        if (bufferedImage != null) {
            val bitmap = remember(bufferedImage) { bufferedImage.toComposeImageBitmap() }
            val imageWidth = bufferedImage.width
            val imageHeight = bufferedImage.height
            val imageSize = IntSize(imageWidth, imageHeight)

            // 1. [底层] 绘制画布 (图片 + 网格 + 切割框)
            // 它必须放在前面，作为背景
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(gridColor) // 透明背景网格

                withTransform({
                    translate(left = offset.x + center.x, top = offset.y + center.y)
                    scale(scale, Offset.Zero)
                    translate(left = -imageWidth / 2f, top = -imageHeight / 2f)
                }) {
                    drawImage(bitmap, filterQuality = FilterQuality.None)
                    onDrawOverlay() // 切割框在这里绘制
                }
            }

            // 2. [顶层] 绘制悬浮层 (放大镜 / 信息条)
            // 放在 Canvas 后面，确保覆盖在图片之上
            if (hoverPos != null) {
                val pixelPos = CanvasUtils.screenToImage(
                    hoverPos!!, canvasSize, IntSize(imageWidth, imageHeight), scale, offset
                )
                val inBounds = pixelPos.x in 0 until imageWidth && pixelPos.y in 0 until imageHeight

                if (inBounds) {
                    if (showMagnifier) {
                        // 2.1 放大镜模式
                        androidx.compose.foundation.layout.Box(
                            // [核心修复] 强制提升 Z 轴层级，确保绝对置顶
                            modifier = Modifier.zIndex(100f)
                        ) {
                            MagnifierOverlay(
                                sourceImage = displayImage.image,
                                centerPixel = pixelPos,
                                screenPos = hoverPos!!,
                                zoomLevel = 10,
                                gridSize = 15
                            )
                        }
                    } else {
                        // 2.2 普通模式 (悬停条)
                        val colorInt = try { displayImage.image.getRGB(pixelPos.x, pixelPos.y) } catch(e:Exception){ 0 }
                        HoverInfoOverlay(
                            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
                            mouseScreenPos = hoverPos!!,
                            canvasSize = canvasSize,
                            imageSize = IntSize(imageWidth, imageHeight),
                            scale = scale,
                            offset = offset,
                            color = Color(colorInt)
                        )
                    }
                } else if (!showMagnifier) {
                    // 出界显示
                    HoverInfoOverlay(
                        modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
                        mouseScreenPos = hoverPos!!,
                        canvasSize = canvasSize,
                        imageSize = IntSize(imageWidth, imageHeight),
                        scale = scale,
                        offset = offset,
                        color = null
                    )
                }
            }

            if (isSelectingRegion) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(200f) // 确保在最上层
                ) {
                    RegionSelectorOverlay(
                        onRegionSelected = { screenRect ->
                            // === 核心逻辑：这里复用 CanvasUtils 进行坐标转换 ===
                            // 1. 将屏幕矩形的左上角转为图片坐标
                            val topLeftScreen = Offset(screenRect.x.toFloat(), screenRect.y.toFloat())
                            val topLeftImage = CanvasUtils.screenToImage(
                                topLeftScreen, canvasSize, imageSize, scale, offset
                            )

                            // 2. 将屏幕矩形的右下角转为图片坐标
                            val bottomRightScreen = Offset(
                                (screenRect.x + screenRect.width).toFloat(),
                                (screenRect.y + screenRect.height).toFloat()
                            )
                            val bottomRightImage = CanvasUtils.screenToImage(
                                bottomRightScreen, canvasSize, imageSize, scale, offset
                            )

                            // 3. 计算出修正后的矩形 (处理可能的反向拖拽)
                            val x = min(topLeftImage.x, bottomRightImage.x)
                            val y = min(topLeftImage.y, bottomRightImage.y)
                            val w = abs(topLeftImage.x - bottomRightImage.x)
                            val h = abs(topLeftImage.y - bottomRightImage.y)

                            // 4. 返回真实的图片坐标
                            onRegionSelected(IntRect(x, y, w, h))
                        },
                        onCancel = { /* 可以增加一个取消回调 */ }
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

private fun calculateTapEvent(
    screenPos: Offset,
    displayImage: ImageLayer?,
    boxSize: IntSize,
    scale: Float,
    offset: Offset
): CanvasTapEvent? {
    val bufferedImage = displayImage?.image ?: return null
    val canvasSize = Size(boxSize.width.toFloat(), boxSize.height.toFloat())

    val pixelPos = CanvasUtils.screenToImage(
        screenPos, canvasSize, IntSize(bufferedImage.width, bufferedImage.height), scale, offset
    )

    var color = Color.Transparent
    var isToBounds = false

    if (pixelPos.x in 0 until bufferedImage.width && pixelPos.y in 0 until bufferedImage.height) {
        try {
            val rgb = bufferedImage.getRGB(pixelPos.x, pixelPos.y)
            color = Color(rgb)
            isToBounds = true
        } catch (e: Exception) { /* ignore */ }
    }
    return CanvasTapEvent(screenPos, pixelPos, color, isToBounds)
}

@Composable
fun HoverInfoOverlay(
    modifier: Modifier,
    mouseScreenPos: Offset,
    canvasSize: Size,
    imageSize: IntSize,
    scale: Float,
    offset: Offset,
    color: Color?
) {
    val pixelPos = CanvasUtils.screenToImage(mouseScreenPos, canvasSize, imageSize, scale, offset)
    val inBounds = pixelPos.x in 0 until imageSize.width && pixelPos.y in 0 until imageSize.height

    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                MaterialTheme.shapes.small
            )
            .padding(8.dp)
    ) {
        val colorHex = if (color != null) {
            val argb = color.value.toLong()
            val r = (argb shr 16 and 0xFF).toString(16).padStart(2, '0').uppercase()
            val g = (argb shr 8 and 0xFF).toString(16).padStart(2, '0').uppercase()
            val b = (argb and 0xFF).toString(16).padStart(2, '0').uppercase()
            "#$r$g$b"
        } else "--"

        val text = if (inBounds) {
            "X: ${pixelPos.x}  Y: ${pixelPos.y}\nColor: $colorHex"
        } else {
            "Out of bounds"
        }

        Text(
            text = text,
            style = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        )
    }
}