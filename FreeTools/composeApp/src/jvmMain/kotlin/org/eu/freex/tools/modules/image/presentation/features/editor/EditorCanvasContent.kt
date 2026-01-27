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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import org.eu.freex.tools.modules.image.domain.model.ImageLayer
import org.eu.freex.tools.modules.image.presentation.features.editor.components.MagnifierOverlay
import org.eu.freex.tools.modules.image.presentation.features.editor.strategies.CanvasTabStrategy
import org.eu.freex.tools.modules.image.presentation.features.editor.utils.CanvasUtils

/**
 * [EditorCanvasContent]
 * 核心渲染组件 (Dumb Component)。
 * 它不包含任何业务逻辑，完全由 [strategy] 驱动。
 */
@Composable
fun EditorCanvasContent(
    modifier: Modifier = Modifier,
    displayImage: ImageLayer?,
    strategy: CanvasTabStrategy
) {
    // === 1. 画布状态 ===
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var hoverPos by remember { mutableStateOf<Offset?>(null) }

    val textMeasurer = rememberTextMeasurer()

    // === 2. 手势处理 ===
    // 只有当策略允许缩放平移时，才启用 Transformable
    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        if (strategy.enableZoomPan) {
            scale = (scale * zoomChange).coerceIn(0.1f, 10f)
            offset += panChange
        }
    }

    // 辅助颜色
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerLowest
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .clipToBounds()
            .pointerHoverIcon(strategy.getCursorIcon()) // 动态光标
            // 缩放平移手势
            .then(if (strategy.enableZoomPan) Modifier.transformable(state = transformableState) else Modifier)
            // 鼠标悬停监听 (用于放大镜/坐标显示)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull()
                        if (event.type == PointerEventType.Move && change != null) {
                            hoverPos = change.position
                        } else if (event.type == PointerEventType.Exit) {
                            hoverPos = null
                        }
                    }
                }
            }
            // 点击手势
            .pointerInput(displayImage, strategy, scale, offset) {
                detectTapGestures(
                    onTap = { screenPos ->
                        // [修正点] 使用 size.toSize() 将 IntSize 转换为 Size
                        val details = CanvasUtils.calculateTapDetails(
                            screenPos,
                            displayImage,
                            size.toSize(), // 这里修改
                            scale,
                            offset
                        )
                        if (details != null) {
                            strategy.onTap(details.pixelPos.x, details.pixelPos.y, details.color)
                        }
                    },
                    onDoubleTap = { screenPos ->
                        // [修正点]同样这里也要修改
                        val details = CanvasUtils.calculateTapDetails(
                            screenPos,
                            displayImage,
                            size.toSize(), // 这里修改
                            scale,
                            offset
                        )
                        if (details != null) {
                            strategy.onDoubleTap(details.pixelPos.x, details.pixelPos.y)
                        }
                    }
                )
            }
    ) {
        val canvasSize = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())

        if (displayImage?.image != null) {
            val bufferedImage = displayImage.image
            val bitmap = remember(bufferedImage) { bufferedImage.toComposeImageBitmap() }
            val imageWidth = bufferedImage.width
            val imageHeight = bufferedImage.height
            val imageSize = IntSize(imageWidth, imageHeight)

            // === 3. 核心绘制层 (Canvas) ===
            Canvas(modifier = Modifier.fillMaxSize()) {
                // 3.1 绘制背景网格 (可选，这里简化为纯色或简单线条)
                drawRect(gridColor)

                // 3.2 应用变换 (缩放 + 平移)
                withTransform({
                    translate(left = offset.x + center.x, top = offset.y + center.y)
                    scale(scale, Offset.Zero)
                    translate(left = -imageWidth / 2f, top = -imageHeight / 2f)
                }) {
                    // 3.3 绘制底图
                    drawImage(bitmap, filterQuality = FilterQuality.None)

                    // 3.4 [策略钩子] 绘制业务 Overlay (在图片坐标系下)
                    with(strategy) {
                        drawOverlay(textMeasurer)
                    }
                }
            }

            // === 4. 组合层 Overlay (如框选组件) ===
            // 这些组件通常覆盖在 Canvas 之上，可能处理自己的手势
            strategy.ContentOverlay(modifier = Modifier.fillMaxSize())

            // === 5. 悬浮工具 (放大镜 / 信息条) ===
            if (hoverPos != null) {
                val pixelPos = CanvasUtils.screenToImage(hoverPos!!, canvasSize, imageSize, scale, offset)
                val inBounds = pixelPos.x in 0 until imageWidth && pixelPos.y in 0 until imageHeight

                // 策略决定是否显示放大镜，且鼠标必须在图片范围内
                if (inBounds && strategy.showMagnifier) {
                    Box(modifier = Modifier.zIndex(200f)) {
                        MagnifierOverlay(
                            sourceImage = bufferedImage,
                            centerPixel = pixelPos,
                            screenPos = hoverPos!!,
                            zoomLevel = 10,
                            gridSize = 15
                        )
                    }
                } else {
                    // 默认显示坐标信息 (如果策略没要求放大镜，或者鼠标在图片外)
                    val color = if (inBounds) try {
                        Color(bufferedImage.getRGB(pixelPos.x, pixelPos.y))
                    } catch (e: Exception) {
                        null
                    } else null

                    HoverInfoOverlay(
                        modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).zIndex(190f),
                        pixelPos = pixelPos,
                        inBounds = inBounds,
                        color = color
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

// 简单的悬浮信息组件 (提取出来保持代码整洁)
@Composable
private fun HoverInfoOverlay(
    modifier: Modifier,
    pixelPos: androidx.compose.ui.unit.IntOffset,
    inBounds: Boolean,
    color: Color?
) {
    Box(
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