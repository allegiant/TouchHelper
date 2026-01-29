package org.eu.freex.tools.common.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntRect
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

object ImageUtils {

    fun cropImage(image: BufferedImage, rect: IntRect): BufferedImage {
        val x = rect.left.coerceAtLeast(0)
        val y = rect.top.coerceAtLeast(0)
        val w = rect.width.coerceAtMost(image.width - x)
        val h = rect.height.coerceAtMost(image.height - y)
        return image.getSubimage(x, y, w, h)
    }

    // ========================================================================
    // ⚡️ 性能优化核心区
    // ========================================================================

    fun toRgbaPixels(image: BufferedImage): ByteArray {
        val width = image.width
        val height = image.height
        val pixels = IntArray(width * height)
        image.getRGB(0, 0, width, height, pixels, 0, width)
        val bytes = ByteArray(width * height * 4)
        for (i in pixels.indices) {
            val argb = pixels[i]
            val a = (argb ushr 24) and 0xFF
            val r = (argb ushr 16) and 0xFF
            val g = (argb ushr 8) and 0xFF
            val b = argb and 0xFF
            val offset = i * 4
            bytes[offset] = r.toByte()
            bytes[offset + 1] = g.toByte()
            bytes[offset + 2] = b.toByte()
            bytes[offset + 3] = a.toByte()
        }
        return bytes
    }

    fun fromRgbaPixels(width: Int, height: Int, bytes: ByteArray): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val pixels = IntArray(width * height)
        for (i in pixels.indices) {
            val offset = i * 4
            val r = bytes[offset].toInt() and 0xFF
            val g = bytes[offset + 1].toInt() and 0xFF
            val b = bytes[offset + 2].toInt() and 0xFF
            val a = bytes[offset + 3].toInt() and 0xFF
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        image.setRGB(0, 0, width, height, pixels, 0, width)
        return image
    }

    @Deprecated("性能较差，建议迁移到 Rust Raw Pixel 接口")
    fun bufferedImageToBytes(image: BufferedImage, format: String = "png"): ByteArray {
        val outputStream = ByteArrayOutputStream()
        ImageIO.write(image, format, outputStream)
        return outputStream.toByteArray()
    }

    // --- 【新增】安全读取 ---
    fun read(file: File): BufferedImage? {
        return try {
            ImageIO.read(file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // --- 【新增】保存图片 ---
    fun save(image: BufferedImage, file: File) {
        val name = file.name.lowercase()
        val format = when {
            name.endsWith(".jpg") || name.endsWith(".jpeg") -> "jpg"
            name.endsWith(".bmp") -> "bmp"
            name.endsWith(".gif") -> "gif"
            else -> "png"
        }
        file.parentFile?.mkdirs()
        if (!ImageIO.write(image, format, file)) {
            throw RuntimeException("未找到适合格式 '$format' 的图片写入器")
        }
    }

    /**
     * 将 "001101..." 格式的二值化字符串还原为图片
     * @param width 图片宽
     * @param height 图片高
     * @param binary 0/1 字符串
     */
    fun binaryStringToBitmap(width: Int, height: Int, binary: String): ImageBitmap {
        val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

        // 限制长度防止越界
        val length = minOf(binary.length, width * height)

        // 定义颜色 (使用 java.awt.Color 常量更安全)
        val colorForeground = java.awt.Color.BLACK.rgb      // 前景：红色
        val colorTransparent = 0x00000000          // 背景：全透明

        for (i in 0 until length) {
            val x = i % width
            val y = i / width
            val char = binary[i]

            if (char == '1') {
                bufferedImage.setRGB(x, y, colorForeground)
            } else {
                bufferedImage.setRGB(x, y, colorTransparent)
            }
        }
        return bufferedImage.toComposeImageBitmap()
    }

    /**
     * 二进制串 "0011..." 转 Hex "3..."
     */
    fun binaryStringToHex(bin: String): String {
        val sb = StringBuilder()
        var padded = bin
        // 补齐 4 位
        while (padded.length % 4 != 0) {
            padded += "0"
        }
        for (i in padded.indices step 4) {
            val chunk = padded.substring(i, i + 4)
            val decimal = chunk.toInt(2)
            // 转 16 进制，大写
            sb.append(decimal.toString(16).uppercase())
        }
        return sb.toString()
    }

    /**
     * Color Int 转 Hex 字符串 (例如 "#FF0000")
     */
    fun colorToHex(color: Int, hasAlpha: Boolean = false): String {
        val alpha = (color ushr 24) and 0xFF
        val red = (color ushr 16) and 0xFF
        val green = (color ushr 8) and 0xFF
        val blue = color and 0xFF

        return if (hasAlpha) {
            String.format("#%02X%02X%02X%02X", alpha, red, green, blue)
        } else {
            String.format("#%02X%02X%02X", red, green, blue)
        }
    }

    /**
     * 安全获取子区域像素 (越界部分填充透明)
     * 用于放大镜在边缘时的显示
     */
    fun getSafePixels(image: BufferedImage, x: Int, y: Int, w: Int, h: Int): IntArray {
        val pixels = IntArray(w * h) { 0 } // 默认全 0 (透明)

        // 计算与原图的交集区域
        val imgW = image.width
        val imgH = image.height

        val safeX = x.coerceAtLeast(0)
        val safeY = y.coerceAtLeast(0)
        val safeW = (x + w).coerceAtMost(imgW) - safeX
        val safeH = (y + h).coerceAtMost(imgH) - safeY

        if (safeW > 0 && safeH > 0) {
            // 临时数组存有效区域
            val validPixels = IntArray(safeW * safeH)
            image.getRGB(safeX, safeY, safeW, safeH, validPixels, 0, safeW)

            // 将有效区域搬运到目标数组 (处理偏移)
            val offsetX = safeX - x
            val offsetY = safeY - y

            for (row in 0 until safeH) {
                System.arraycopy(
                    validPixels, row * safeW,
                    pixels, (row + offsetY) * w + offsetX,
                    safeW
                )
            }
        }
        return pixels
    }


    /**
     * 安全获取 BufferedImage 指定坐标的颜色
     */
    fun getPixelColor(image: BufferedImage, x: Int, y: Int): Color {
        return try {
            if (x in 0 until image.width && y in 0 until image.height) {
                Color(image.getRGB(x, y))
            } else {
                Color.Transparent
            }
        } catch (e: Exception) {
            Color.Transparent
        }
    }
}