package org.eu.freex.tools.modules.image.presentation.features.editor.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.eu.freex.tools.common.utils.ImageUtils
import java.awt.image.BufferedImage
import kotlin.math.roundToInt

@Composable
fun MagnifierOverlay(
    sourceImage: BufferedImage,
    centerPixel: IntOffset,
    screenPos: Offset,
    zoomLevel: Int = 12,
    gridSize: Int = 15
) {
    val textMeasurer = rememberTextMeasurer()

    // [修复点 1] 在 Composable 作用域内获取主题颜色
    val textColor = MaterialTheme.colorScheme.onSurface

    val totalSize = (gridSize * zoomLevel).dp
    val halfGrid = gridSize / 2

    val offsetX = screenPos.x + 20f
    val offsetY = screenPos.y + 20f

    val pixels = remember(sourceImage, centerPixel) {
        val startX = centerPixel.x - halfGrid
        val startY = centerPixel.y - halfGrid
        ImageUtils.getSafePixels(sourceImage, startX, startY, gridSize, gridSize)
    }

    val centerColorInt = remember(pixels) {
        val centerIndex = halfGrid * gridSize + halfGrid
        if (centerIndex in pixels.indices) pixels[centerIndex] else 0
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .size(totalSize)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(Color.Black)
            .border(2.dp, Color.White, CircleShape)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            for (y in 0 until gridSize) {
                for (x in 0 until gridSize) {
                    val colorInt = pixels[y * gridSize + x]
                    val color = Color(colorInt)

                    val rectX = x * zoomLevel.toFloat()
                    val rectY = y * zoomLevel.toFloat()

                    drawRect(
                        color = color,
                        topLeft = Offset(rectX, rectY),
                        size = Size(zoomLevel.toFloat(), zoomLevel.toFloat())
                    )

                    drawRect(
                        color = Color.Gray.copy(alpha = 0.3f),
                        topLeft = Offset(rectX, rectY),
                        size = Size(zoomLevel.toFloat(), zoomLevel.toFloat()),
                        style = Stroke(width = 1f)
                    )
                }
            }

            val centerX = halfGrid * zoomLevel.toFloat()
            val centerY = halfGrid * zoomLevel.toFloat()

            drawRect(
                color = Color.Red,
                topLeft = Offset(centerX, centerY),
                size = Size(zoomLevel.toFloat(), zoomLevel.toFloat()),
                style = Stroke(width = 2f)
            )
            drawLine(Color.Red, Offset(centerX + zoomLevel/2, 0f), Offset(centerX + zoomLevel/2, size.height), strokeWidth = 1f)
            drawLine(Color.Red, Offset(0f, centerY + zoomLevel/2), Offset(size.width, centerY + zoomLevel/2), strokeWidth = 1f)
        }
    }

    // 信息栏 Box
    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), (offsetY + totalSize.toPx() + 8).roundToInt()) }
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f), MaterialTheme.shapes.small)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
            .padding(8.dp)
    ) {
        // [优化] 这里直接用 Text 组件其实更简单，不需要用 Canvas
        // 但既然为了保持代码结构一致，我们继续用 Canvas，只是传入刚才获取的 textColor
        Canvas(modifier = Modifier.size(120.dp, 40.dp)) {
            val hex = ImageUtils.colorToHex(centerColorInt)
            val r = (centerColorInt ushr 16) and 0xFF
            val g = (centerColorInt ushr 8) and 0xFF
            val b = centerColorInt and 0xFF

            val text = "X:${centerPixel.x} Y:${centerPixel.y}\n$hex ($r,$g,$b)"

            drawText(
                textMeasurer = textMeasurer,
                text = text,
                style = TextStyle(
                    color = textColor, // [修复点 2] 使用外部捕获的颜色
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}