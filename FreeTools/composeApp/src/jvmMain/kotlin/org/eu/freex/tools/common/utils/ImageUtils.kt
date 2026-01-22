package org.eu.freex.tools.common.utils

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.Color
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Toolkit
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilenameFilter
import javax.imageio.ImageIO

object ImageUtils {

    fun pickFile(): File? {
        val dialog = FileDialog(null as Frame?, "导入图片", FileDialog.LOAD)
        dialog.filenameFilter = FilenameFilter { _, name ->
            val n = name.lowercase()
            n.endsWith(".png") || n.endsWith(".jpg") ||
                    n.endsWith(".jpeg") || n.endsWith(".bmp") || n.endsWith(".webp")
        }
        dialog.isVisible = true
        val dir = dialog.directory
        val file = dialog.file
        return if (dir != null && file != null) File(dir, file) else null
    }

    fun captureFullScreen(): BufferedImage {
        val screenRect = Rectangle(Toolkit.getDefaultToolkit().screenSize)
        return Robot().createScreenCapture(screenRect)
    }

    fun cropImage(image: BufferedImage, rect: Rect): BufferedImage {
        val x = rect.left.toInt().coerceAtLeast(0)
        val y = rect.top.toInt().coerceAtLeast(0)
        val w = rect.width.toInt().coerceAtMost(image.width - x)
        val h = rect.height.toInt().coerceAtMost(image.height - y)
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

    fun load(file: File): BufferedImage {
        return ImageIO.read(file)
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
        val colorForeground = Color.BLACK.rgb      // 前景：红色
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
}