package org.eu.freex.tools.modules.image.data.repository

import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.eu.freex.tools.common.utils.ImageUtils
import org.eu.freex.tools.modules.image.domain.model.*
import org.eu.freex.tools.modules.image.domain.repository.LayerRepository
import java.awt.image.BufferedImage
import java.io.File
import kotlin.math.roundToInt

// Rust Bindings
import uniffi.touch_core.applyBinarization
import uniffi.touch_core.applyBlackwhiteInvert
import uniffi.touch_core.applyColorInvert
import uniffi.touch_core.applyDenoise
import uniffi.touch_core.applyGrayscale
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Window
import uniffi.touch_core.BinarizationFilter as RustBinarizationFilter
import uniffi.touch_core.BlackWhiteInvertFilter as RustBlackWhiteInvertFilter
import uniffi.touch_core.ColorInvertFilter as RustColorInvertFilter
import uniffi.touch_core.DenoiseFilter as RustDenoiseFilter
import uniffi.touch_core.GrayscaleFilter as RustGrayscaleFilter
import uniffi.touch_core.scanComponents as rustScanComponents

class LayerRepositoryImpl : LayerRepository {

    override suspend fun loadFromFile(file: File): BufferedImage = withContext(Dispatchers.IO) {
        ImageUtils.read(file) ?: throw Exception("Read failed: ${file.name}")
    }

    override suspend fun saveToFile(image: BufferedImage, file: File) = withContext(Dispatchers.IO) {
        ImageUtils.save(image, file)
    }

    override suspend fun captureScreen(): BufferedImage {
        // 1. 【UI 操作】在主线程隐藏窗口
        val visibleWindows = withContext(Dispatchers.Main) {
            val windows = Window.getWindows().filter { it.isVisible }
            windows.forEach { it.isVisible = false }
            windows
        }

        return try {
            // 给窗口隐藏一点时间动画
            delay(300)

            withContext(Dispatchers.IO) {
                val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
                val screens = ge.screenDevices
                var logicalBounds = Rectangle()

                // 计算所有屏幕的逻辑总大小
                for (screen in screens) {
                    logicalBounds = logicalBounds.union(screen.defaultConfiguration.bounds)
                }

                val robot = Robot()

                // 使用多分辨率截图 (适配高分屏)
                val mri = robot.createMultiResolutionScreenCapture(logicalBounds)
                val variants = mri.resolutionVariants

                // 获取最高清的物理像素图
                val bestVariant = variants.maxByOrNull { it.getWidth(null) }

                bestVariant as? BufferedImage ?: robot.createScreenCapture(logicalBounds)
            }
        } finally {
            // 2. 【UI 操作】恢复窗口显示
            withContext(Dispatchers.Main) {
                visibleWindows.forEach {
                    it.isVisible = true
                    it.toFront()
                }
            }
        }
    }


    override suspend fun applyFilter(source: BufferedImage, filter: ImageFilter): BufferedImage =
        withContext(Dispatchers.Default) {
            val pixels = ImageUtils.toRgbaPixels(source)
            val w = source.width
            val h = source.height

            val resultPixels = try {
                when (filter) {
                    is ViewFilter -> pixels
                    is BinarizationFilter -> {
                        val min = filter.min.roundToInt().coerceIn(0, 255)
                        val max = filter.max.roundToInt().coerceIn(0, 255)
                        applyBinarization(pixels, w, h, RustBinarizationFilter(min, max, filter.isRgbAvg))
                    }

                    is GrayscaleFilter -> applyGrayscale(pixels, w, h, RustGrayscaleFilter())
                    is ColorInvertFilter -> applyColorInvert(pixels, w, h, RustColorInvertFilter())
                    is DenoiseFilter -> applyDenoise(pixels, w, h, RustDenoiseFilter(filter.radius))
                    is BlackWhiteInvertFilter -> applyBlackwhiteInvert(pixels, w, h, RustBlackWhiteInvertFilter())
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext source
            }
            ImageUtils.fromRgbaPixels(w, h, resultPixels)
        }

    override suspend fun segment(source: BufferedImage, segmentation: Segmentation): List<Rect> =
        withContext(Dispatchers.Default) {
            val pixels = ImageUtils.toRgbaPixels(source)
            val w = source.width
            val h = source.height

            val rustRects = when (segmentation) {
                is GridSegmentation -> rustScanComponents(
                    pixels,
                    w,
                    h,
                    emptyList(),
                    true,
                    segmentation.rowCount,
                    segmentation.colCount
                )

                is AutoSegmentation -> rustScanComponents(pixels, w, h, emptyList(), false, null, null)
            }

            // 【修复】显式转换为 Int，因为 Rust 返回的可能是 Long (u32/i64)
            rustRects.map {
                Rect(it.left.toFloat(), it.top.toFloat(), it.width.toFloat(), it.height.toFloat())
            }
        }

    override suspend fun crop(source: BufferedImage, rect: Rect): BufferedImage = withContext(Dispatchers.Default) {
        ImageUtils.cropImage(source, rect)
    }
}