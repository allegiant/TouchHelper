package org.eu.freex.tools.common.utils

import androidx.compose.ui.geometry.Rect
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
        // 使用 java.awt.FileDialog 代替 JFileChooser
        // FileDialog 会调用系统的原生文件选择窗口 (Native OS Dialog)
        val dialog = FileDialog(null as Frame?, "导入图片", FileDialog.LOAD)

        // 设置过滤 (虽然 FileDialog 的过滤在不同系统表现不一，但这是获取原生外观的最简方法)
        dialog.filenameFilter = FilenameFilter { _, name ->
            val n = name.lowercase()
            n.endsWith(".png") || n.endsWith(".jpg") ||
                    n.endsWith(".jpeg") || n.endsWith(".bmp") || n.endsWith(".webp")
        }

        // 显示窗口（这行代码会阻塞，直到用户关闭窗口）
        dialog.isVisible = true

        val dir = dialog.directory
        val file = dialog.file

        return if (dir != null && file != null) {
            File(dir, file)
        } else {
            null
        }
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
    // ⚡️ 性能优化核心区：原始像素处理 (替代 PNG 编解码)
    // ========================================================================

    /**
     * 将 BufferedImage 转换为原始 RGBA 字节数组
     * 性能：比 ImageIO.write 极快
     */
    fun toRgbaPixels(image: BufferedImage): ByteArray {
        val width = image.width
        val height = image.height
        val pixels = IntArray(width * height)

        // 1. 获取 ARGB 整数数组 (非常快)
        image.getRGB(0, 0, width, height, pixels, 0, width)

        // 2. 转换为 RGBA 字节数组 (Rust 友好格式)
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

    /**
     * 将原始 RGBA 字节数组转回 BufferedImage
     */
    fun fromRgbaPixels(width: Int, height: Int, bytes: ByteArray): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val pixels = IntArray(width * height)

        for (i in pixels.indices) {
            val offset = i * 4
            val r = bytes[offset].toInt() and 0xFF
            val g = bytes[offset + 1].toInt() and 0xFF
            val b = bytes[offset + 2].toInt() and 0xFF
            val a = bytes[offset + 3].toInt() and 0xFF

            // 组装回 ARGB int
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        image.setRGB(0, 0, width, height, pixels, 0, width)
        return image
    }

    // 保留旧方法作为兼容或备用
    @Deprecated("性能较差，建议迁移到 Rust Raw Pixel 接口")
    fun bufferedImageToBytes(image: BufferedImage, format: String = "png"): ByteArray {
        val outputStream = ByteArrayOutputStream()
        ImageIO.write(image, format, outputStream)
        return outputStream.toByteArray()
    }


    fun load(file: File): BufferedImage {
        return ImageIO.read(file)
    }

}