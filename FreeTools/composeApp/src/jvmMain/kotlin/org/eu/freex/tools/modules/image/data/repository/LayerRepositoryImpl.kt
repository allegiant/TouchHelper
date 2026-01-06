package org.eu.freex.tools.modules.image.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.eu.freex.tools.common.utils.ImageUtils
import org.eu.freex.tools.modules.image.domain.model.*
import org.eu.freex.tools.modules.image.domain.repository.LayerRepository
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.File
import kotlin.math.roundToInt

// Rust Bindings
import uniffi.touch_core.applyBinarization
import uniffi.touch_core.applyBlackwhiteInvert
import uniffi.touch_core.applyColorInvert
import uniffi.touch_core.applyDenoise
import uniffi.touch_core.applyGrayscale
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

    override suspend fun captureScreen(): BufferedImage = withContext(Dispatchers.IO) {
        delay(300)
        ImageUtils.captureFullScreen() ?: throw Exception("Screen capture failed")
    }

    override suspend fun applyFilter(source: BufferedImage, filter: ImageFilter): BufferedImage = withContext(Dispatchers.Default) {
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

    override suspend fun segment(source: BufferedImage, segmentation: Segmentation): List<Rectangle> = withContext(Dispatchers.Default) {
        val pixels = ImageUtils.toRgbaPixels(source)
        val w = source.width
        val h = source.height

        val rustRects = when (segmentation) {
            is GridSegmentation -> rustScanComponents(pixels, w, h, emptyList(), true, segmentation.rowCount, segmentation.colCount)
            is AutoSegmentation -> rustScanComponents(pixels, w, h, emptyList(), false, null, null)
        }

        // 【修复】显式转换为 Int，因为 Rust 返回的可能是 Long (u32/i64)
        rustRects.map {
            Rectangle(it.left, it.top, it.width.toInt(), it.height.toInt())
        }
    }

    override suspend fun crop(source: BufferedImage, rect: Rectangle): BufferedImage = withContext(Dispatchers.Default) {
        ImageUtils.cropImage(source, androidx.compose.ui.geometry.Rect(
            rect.x.toFloat(), rect.y.toFloat(),
            (rect.x + rect.width).toFloat(), (rect.y + rect.height).toFloat()
        ))
    }
}