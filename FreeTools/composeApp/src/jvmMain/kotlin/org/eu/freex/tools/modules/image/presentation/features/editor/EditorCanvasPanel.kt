package org.eu.freex.tools.modules.image.presentation.features.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.modules.image.domain.model.ImageLayer
import java.awt.Color as AwtColor

@Composable
fun EditorCanvasPanel(
    modifier: Modifier = Modifier,
    displayLayer: ImageLayer?,
    // 【修改点 1】新增三个参数，用于控制取色逻辑
    isPicking: Boolean,             // 当前是否处于取色模式
    onPick: (Color) -> Unit,        // 确认取色回调
    onCancel: () -> Unit            // 取消取色回调
) {
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerLow
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    val highlightColor = MaterialTheme.colorScheme.error
    val placeholderColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        if (displayLayer?.image == null) {
            Text("请导入或选择图片", color = placeholderColor)
            return@Box
        }

        val bufferedImage = displayLayer.image
        val bitmap = remember(bufferedImage) { bufferedImage.toComposeImageBitmap() }

        var scale by remember { mutableStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var hoverPixel by remember { mutableStateOf<IntOffset?>(null) }
        var hoverColor by remember { mutableStateOf<AwtColor?>(null) }

        // 当切图层时重置
        LaunchedEffect(displayLayer.id) {
            scale = 1f
            offset = Offset.Zero
            hoverPixel = null
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // 1. 鼠标移动与滚轮 (保持原有逻辑不变)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val canvasCenter = Offset(size.width / 2f, size.height / 2f)
                            val pointerPos = event.changes.first().position

                            val imgWidth = bitmap.width
                            val imgHeight = bitmap.height

                            val relativeToCenter = pointerPos - canvasCenter - offset
                            val unscaledRelative = relativeToCenter / scale

                            val pixelX = (unscaledRelative.x + imgWidth / 2f).toInt()
                            val pixelY = (unscaledRelative.y + imgHeight / 2f).toInt()

                            if (event.type == PointerEventType.Move) {
                                if (pixelX in 0 until imgWidth && pixelY in 0 until imgHeight) {
                                    hoverPixel = IntOffset(pixelX, pixelY)
                                    val rgb = bufferedImage.getRGB(pixelX, pixelY)
                                    hoverColor = AwtColor(rgb, true)
                                } else {
                                    hoverPixel = null
                                    hoverColor = null
                                }
                            } else if (event.type == PointerEventType.Scroll) {
                                val change = event.changes.first()
                                val scrollDelta = change.scrollDelta.y
                                val zoomFactor = 1.1f
                                val newScale = if (scrollDelta < 0) scale * zoomFactor else scale / zoomFactor
                                val clampedScale = newScale.coerceIn(0.1f, 50f)

                                val pLocal = (pointerPos - canvasCenter - offset) / scale
                                val newOffset = offset + pLocal * (scale - clampedScale)

                                scale = clampedScale
                                offset = newOffset
                                change.consume()
                            }
                        }
                    }
                }
                // 2. 触摸板手势 (保持原有逻辑不变)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.1f, 50f)
                        offset += pan
                    }
                }
                // 3. 点击事件 (【修改点 2】根据 isPicking 状态切换逻辑)
                .pointerInput(isPicking) { // 依赖 key 设为 isPicking，当状态改变时重新绑定手势
                    if (isPicking) {
                        // === 取色模式 ===
                        detectTapGestures(
                            onTap = { tapOffset ->
                                val canvasCenter = Offset(size.width / 2f, size.height / 2f)
                                val imgWidth = bitmap.width
                                val imgHeight = bitmap.height

                                // 坐标计算 (复用上面的逻辑)
                                val relativeToCenter = tapOffset - canvasCenter - offset
                                val unscaledRelative = relativeToCenter / scale
                                val pixelX = (unscaledRelative.x + imgWidth / 2f).toInt()
                                val pixelY = (unscaledRelative.y + imgHeight / 2f).toInt()

                                // 边界检查
                                if (pixelX in 0 until imgWidth && pixelY in 0 until imgHeight) {
                                    val rgb = bufferedImage.getRGB(pixelX, pixelY)
                                    // 触发确认回调
                                    onPick(Color(rgb))
                                }
                            },
                            onLongPress = {
                                // 右键或长按取消
                                onCancel()
                            }
                        )
                    } else {
                        // === 普通模式 (双击复位) ===
                        detectTapGestures(
                            onDoubleTap = { scale = 1f; offset = Offset.Zero }
                        )
                    }
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val imgWidth = bitmap.width.toFloat()
            val imgHeight = bitmap.height.toFloat()

            withTransform({
                translate(offset.x, offset.y)
                scale(scale, pivot = center)
            }) {
                val dstLeft = (canvasWidth - imgWidth) / 2
                val dstTop = (canvasHeight - imgHeight) / 2

                clipRect(left = dstLeft, top = dstTop, right = dstLeft + imgWidth, bottom = dstTop + imgHeight) {
                    drawRect(Color.White, topLeft = Offset(dstLeft, dstTop), size = Size(imgWidth, imgHeight))

                    val checkSize = 10f
                    val cols = (imgWidth / checkSize).toInt() + 1
                    val rows = (imgHeight / checkSize).toInt() + 1

                    for (row in 0 until rows) {
                        for (col in 0 until cols) {
                            if ((row + col) % 2 == 1) {
                                drawRect(
                                    color = Color(0xFFE0E0E0),
                                    topLeft = Offset(dstLeft + col * checkSize, dstTop + row * checkSize),
                                    size = Size(checkSize, checkSize)
                                )
                            }
                        }
                    }
                }

                drawImage(bitmap, topLeft = Offset(dstLeft, dstTop))

                if (scale > 8f) {
                    val strokeWidth = 1f / scale
                    for (x in 0..imgWidth.toInt()) drawLine(gridColor, Offset(dstLeft + x, dstTop), Offset(dstLeft + x, dstTop + imgHeight), strokeWidth)
                    for (y in 0..imgHeight.toInt()) drawLine(gridColor, Offset(dstLeft, dstTop + y), Offset(dstLeft + imgWidth, dstTop + y), strokeWidth)
                }

                hoverPixel?.let { pixel ->
                    drawRect(
                        color = highlightColor,
                        topLeft = Offset(dstLeft + pixel.x, dstTop + pixel.y),
                        size = Size(1f, 1f),
                        style = Stroke(width = 2f / scale)
                    )
                }
            }
        }

        // 底部信息栏 (保持不变)
        if (displayLayer != null) {
            InfoOverlay(
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                hoverPixel = hoverPixel,
                hoverColor = hoverColor,
                scale = scale,
                imgSize = IntSize(displayLayer.image.width, displayLayer.image.height)
            )
        }

        // 【修改点 3】取色模式提示横幅
        if (isPicking) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    "正在取色... 点击图片选取 (长按取消)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

// 辅助组件：InfoOverlay (保持你原有的逻辑，这里直接复制你的代码)
@Composable
private fun InfoOverlay(
    modifier: Modifier,
    hoverPixel: IntOffset?,
    hoverColor: java.awt.Color?,
    scale: Float,
    imgSize: IntSize
) {
    val containerColor = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.8f)
    val contentColor = MaterialTheme.colorScheme.inverseOnSurface

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = "Size: ${imgSize.width}x${imgSize.height} | Zoom: ${(scale * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = contentColor.copy(alpha = 0.8f)
            )

            if (hoverPixel != null && hoverColor != null) {
                Text(
                    text = "XY: (${hoverPixel.x}, ${hoverPixel.y})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.inversePrimary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(12.dp)
                            .background(Color(hoverColor.red, hoverColor.green, hoverColor.blue))
                    )
                    Text(
                        text = "RGB: ${hoverColor.red}, ${hoverColor.green}, ${hoverColor.blue} | " +
                                "#${Integer.toHexString(hoverColor.rgb).uppercase().takeLast(6)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                Text(
                    "Hover image details",
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.5f)
                )
            }
        }
    }
}