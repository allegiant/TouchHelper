package org.eu.freex.tools.modules.image.presentation.features.editor.utils

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * [CanvasUtils]
 * 画布坐标转换工具类。
 * 用于处理 Screen Coordinates (屏幕触控点) 与 Image Pixels (图片实际像素) 之间的相互转换。
 * 核心逻辑剥离自 EditorCanvasPanel，以支持 Overlay 层 (如抓抓、切割框) 的独立绘制。
 */
object CanvasUtils {

    /**
     * 将屏幕坐标转换为图片像素坐标
     *
     * @param screenPos 屏幕上的触控点坐标 (Offset)
     * @param canvasSize 画布组件的当前大小
     * @param imageSize 图片的原始分辨率大小
     * @param scale 当前的缩放比例
     * @param offset 当前的平移偏移量 (Pan Offset)
     * @return 图片上的像素坐标 (IntOffset)
     */
    fun screenToImage(
        screenPos: Offset,
        canvasSize: Size,
        imageSize: IntSize,
        scale: Float,
        offset: Offset
    ): IntOffset {
        // 1. 计算画布中心点
        val canvasCenter = Offset(canvasSize.width / 2f, canvasSize.height / 2f)

        // 2. 计算相对于画布中心的偏移量 (减去平移量)
        val relativeToCenter = screenPos - canvasCenter - offset

        // 3. 反向缩放 (除以 scale)
        val unscaled = relativeToCenter / scale

        // 4. 加上图片的一半宽高，将坐标原点从图片中心移回图片左上角
        val pixelX = unscaled.x + imageSize.width / 2f
        val pixelY = unscaled.y + imageSize.height / 2f

        return IntOffset(pixelX.roundToInt(), pixelY.roundToInt())
    }

    /**
     * 将图片像素坐标转换为屏幕坐标
     * (用于在画布上绘制红框、特征点等 Overlay)
     *
     * @param imagePos 图片上的像素坐标 (IntOffset)
     * @param canvasSize 画布组件的当前大小
     * @param imageSize 图片的原始分辨率大小
     * @param scale 当前的缩放比例
     * @param offset 当前的平移偏移量 (Pan Offset)
     * @return 屏幕上的绘制坐标 (Offset)
     */
    fun imageToScreen(
        imagePos: IntOffset,
        canvasSize: Size,
        imageSize: IntSize,
        scale: Float,
        offset: Offset
    ): Offset {
        // 1. 将坐标原点从图片左上角移到图片中心
        val unscaledX = imagePos.x - imageSize.width / 2f
        val unscaledY = imagePos.y - imageSize.height / 2f

        // 2. 应用缩放
        val scaledX = unscaledX * scale
        val scaledY = unscaledY * scale

        // 3. 计算画布中心点
        val canvasCenter = Offset(canvasSize.width / 2f, canvasSize.height / 2f)

        // 4. 加上平移量和画布中心偏移
        return Offset(scaledX, scaledY) + offset + canvasCenter
    }

    /**
     * 辅助方法：将 Screen Rect 转换为 Image Rect (用于框选裁剪等)
     */
    fun screenRectToImageRect(
        screenRect: Rect,
        canvasSize: Size,
        imageSize: IntSize,
        scale: Float,
        offset: Offset
    ): androidx.compose.ui.unit.IntRect {
        val topLeft = screenToImage(screenRect.topLeft, canvasSize, imageSize, scale, offset)
        val bottomRight = screenToImage(screenRect.bottomRight, canvasSize, imageSize, scale, offset)

        // 确保坐标在图片范围内 (Clamp)
        return androidx.compose.ui.unit.IntRect(
            left = topLeft.x.coerceIn(0, imageSize.width),
            top = topLeft.y.coerceIn(0, imageSize.height),
            right = bottomRight.x.coerceIn(0, imageSize.width),
            bottom = bottomRight.y.coerceIn(0, imageSize.height)
        )
    }

    /**
     * 辅助方法：限制坐标在图片范围内
     */
    fun clampToImage(pos: IntOffset, imageSize: IntSize): IntOffset {
        return IntOffset(
            x = pos.x.coerceIn(0, imageSize.width - 1),
            y = pos.y.coerceIn(0, imageSize.height - 1)
        )
    }
}