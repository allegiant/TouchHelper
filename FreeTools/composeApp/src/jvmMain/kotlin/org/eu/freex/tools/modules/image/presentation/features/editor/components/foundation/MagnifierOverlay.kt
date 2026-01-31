package org.eu.freex.tools.modules.image.presentation.features.editor.components.foundation

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

/**
 * [MagnifierOverlay]
 * 放大镜组件 (纯视觉)。
 * 显示指定像素周围的放大图像和 RGB 信息。
 */
@Composable
fun MagnifierOverlay(
    sourceImage: BufferedImage,
    centerPixel: IntOffset, // 对应的原图像素坐标
    screenPos: Offset,      // 在屏幕(Container)中的显示位置
    zoomLevel: Int = 12,    // 放大倍率
    gridSize: Int = 15      // 网格大小 (15x15像素)
) {
    val textMeasurer = rememberTextMeasurer()
    val textColor = MaterialTheme.colorScheme.onSurface
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    val totalSize = (gridSize * zoomLevel).dp
    val halfGrid = gridSize / 2

    // 偏移一点，避免挡住鼠标
    val offsetX = screenPos.x + 20f
    val offsetY = screenPos.y + 20f

    // 提取像素数据
    val pixels = remember(sourceImage, centerPixel) {
        val startX = centerPixel.x - halfGrid
        val startY = centerPixel.y - halfGrid
        ImageUtils.getSafePixels(sourceImage, startX, startY, gridSize, gridSize)
    }

    val centerColorInt = remember(pixels) {
        val centerIndex = halfGrid * gridSize + halfGrid
        if (centerIndex in pixels.indices) pixels[centerIndex] else 0
    }

    // 1. 放大镜圆圈
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
            // 绘制像素网格
            for (y in 0 until gridSize) {
                for (x in 0 until gridSize) {
                    val colorInt = pixels[y * gridSize + x]
                    val rectX = x * zoomLevel.toFloat()
                    val rectY = y * zoomLevel.toFloat()

                    drawRect(
                        color = Color(colorInt),
                        topLeft = Offset(rectX, rectY),
                        size = Size(zoomLevel.toFloat(), zoomLevel.toFloat())
                    )
                    // 像素边框
                    drawRect(
                        color = Color.Gray.copy(alpha = 0.3f),
                        topLeft = Offset(rectX, rectY),
                        size = Size(zoomLevel.toFloat(), zoomLevel.toFloat()),
                        style = Stroke(width = 1f)
                    )
                }
            }

            // 绘制中心红框 (准星)
            val centerX = halfGrid * zoomLevel.toFloat()
            val centerY = halfGrid * zoomLevel.toFloat()

            drawRect(
                color = Color.Red,
                topLeft = Offset(centerX, centerY),
                size = Size(zoomLevel.toFloat(), zoomLevel.toFloat()),
                style = Stroke(width = 2f)
            )
            // 十字线
            drawLine(Color.Red, Offset(centerX + zoomLevel/2, 0f), Offset(centerX + zoomLevel/2, size.height), strokeWidth = 1f)
            drawLine(Color.Red, Offset(0f, centerY + zoomLevel/2), Offset(size.width, centerY + zoomLevel/2), strokeWidth = 1f)
        }
    }

    // 2. 信息面板 (跟随在放大镜下方)
    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), (offsetY + totalSize.toPx() + 8).roundToInt()) }
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f), MaterialTheme.shapes.small)
            .border(1.dp, borderColor, MaterialTheme.shapes.small)
            .padding(8.dp)
    ) {
        Canvas(modifier = Modifier.size(130.dp, 40.dp)) {
            val hex = ImageUtils.colorToHex(centerColorInt)
            val r = (centerColorInt ushr 16) and 0xFF
            val g = (centerColorInt ushr 8) and 0xFF
            val b = centerColorInt and 0xFF

            val text = "X:${centerPixel.x} Y:${centerPixel.y}\n$hex RGB($r,$g,$b)"

            drawText(
                textMeasurer = textMeasurer,
                text = text,
                style = TextStyle(
                    color = textColor,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}