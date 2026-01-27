package org.eu.freex.tools.modules.image.presentation.features.editor.utils

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.eu.freex.tools.modules.image.domain.model.ImageLayer
import kotlin.math.roundToInt

object CanvasUtils {

    /**
     * 将屏幕坐标 (Screen Coordinates) 转换为图片像素坐标 (Image Pixel Coordinates)。
     * 考虑了当前的缩放 (scale) 和偏移 (offset)。
     */
    fun screenToImage(
        screenPos: Offset,
        canvasSize: Size,
        imageSize: IntSize,
        scale: Float,
        offset: Offset
    ): IntOffset {
        // 1. 计算图片在画布中心的实际显示区域
        // Canvas 的原点 (0,0) 在左上角。
        // 图片被绘制时，先移到中心 (offset + center)，再缩放 (scale)，再移回 (-width/2, -height/2)。

        val centerX = canvasSize.width / 2f + offset.x
        val centerY = canvasSize.height / 2f + offset.y

        val imageLeft = centerX - (imageSize.width * scale) / 2f
        val imageTop = centerY - (imageSize.height * scale) / 2f

        // 2. 逆向计算
        val relativeX = (screenPos.x - imageLeft) / scale
        val relativeY = (screenPos.y - imageTop) / scale

        return IntOffset(relativeX.roundToInt(), relativeY.roundToInt())
    }

    data class TapDetails(val pixelPos: IntOffset, val color: Color)

    /**
     * 计算点击详情：如果点击在图片范围内，返回坐标和颜色；否则返回 null。
     */
    fun calculateTapDetails(
        screenPos: Offset,
        displayImage: ImageLayer?,
        canvasSize: Size,
        scale: Float,
        offset: Offset
    ): TapDetails? {
        val bufferedImage = displayImage?.image ?: return null
        val imageSize = IntSize(bufferedImage.width, bufferedImage.height)

        val pixelPos = screenToImage(screenPos, canvasSize, imageSize, scale, offset)

        if (pixelPos.x in 0 until bufferedImage.width && pixelPos.y in 0 until bufferedImage.height) {
            return try {
                // AWT BufferedImage 坐标系: x, y
                val rgb = bufferedImage.getRGB(pixelPos.x, pixelPos.y)
                TapDetails(pixelPos, Color(rgb))
            } catch (e: Exception) {
                null
            }
        }
        return null
    }
}