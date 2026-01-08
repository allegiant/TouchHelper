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
import uniffi.touch_core.applyDenoise
import uniffi.touch_core.applyDeskew
import uniffi.touch_core.applyExtractBlobs
import uniffi.touch_core.applyExtractContours
import uniffi.touch_core.applyGrayscale
import uniffi.touch_core.applyMorphologyFilter
import uniffi.touch_core.applyMultiColorFilter
import uniffi.touch_core.applyPosterizationFilter
import uniffi.touch_core.applyRemoveLines
import uniffi.touch_core.applyRemoveNoise
import uniffi.touch_core.applyRotate
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Window
import uniffi.touch_core.BinarizationMode as RustBinarizationMode
import uniffi.touch_core.BinarizationFilter as RustBinarizationFilter
import uniffi.touch_core.PosterizationFilter as RustPosterizationFilter
import uniffi.touch_core.MultiColorFilter as RustMultiColorFilter
import uniffi.touch_core.ColorRule as RustColorRule
import uniffi.touch_core.BlackWhiteInvertFilter as RustBlackWhiteInvertFilter
import uniffi.touch_core.DenoiseFilter as RustDenoiseFilter
import uniffi.touch_core.GrayscaleFilter as RustGrayscaleFilter
import uniffi.touch_core.GrayscaleMode as RustGrayscaleMode
import uniffi.touch_core.RemoveNoiseFilter as RustRemoveNoiseFilter
import uniffi.touch_core.RemoveLinesFilter as RustRemoveLinesFilter
import uniffi.touch_core.MorphologyMode as RustMorphologyMode
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
                        val mode: RustBinarizationMode = when (filter.mode) {
                            BinarizationMode.MANUAL -> RustBinarizationMode.MANUAL
                            BinarizationMode.ADAPTIVE -> RustBinarizationMode.ADAPTIVE
                            BinarizationMode.OTSU -> RustBinarizationMode.OTSU
                        }
                        val rustFilter = RustBinarizationFilter(
                            mode,
                            min,
                            max,
                            filter.isRgbAvg,
                            filter.sauvolaK.toDouble(),
                            filter.windowSize.toInt()
                        )
                        applyBinarization(pixels, w, h, rustFilter)
                    }
                    is PosterizationFilter -> {
                        // 映射枚举 Kotlin -> Rust
                        val rustMode = when(filter.mode) {
                            PosterizationMode.RGB -> uniffi.touch_core.PosterizationMode.RGB
                            PosterizationMode.HSV -> uniffi.touch_core.PosterizationMode.HSV
                        }

                        val rustFilter = RustPosterizationFilter(
                            rustMode,
                            filter.isMultiValue,
                            filter.level,
                            filter.channel1,
                            filter.channel2,
                            filter.channel3
                        )
                        applyPosterizationFilter(pixels, w, h, rustFilter)
                    }

                    is MultiColorFilter -> {
                        val rustRules = filter.rules.map { rule ->
                            RustColorRule(
                                rule.id,
                                rule.targetHex,
                                rule.biasHex,
                                rule.isEnabled
                            )
                        }
                        val rustFilter = RustMultiColorFilter(
                            rustRules,
                            filter.isInvert,
                            filter.keepOriginal
                        )
                        applyMultiColorFilter(pixels, w, h, rustFilter)
                    }
                    is GrayscaleFilter -> {
                        // 1. 映射枚举 (Kotlin -> Rust)
                        val rustMode = when (filter.mode) {
                            GrayscaleMode.WEIGHTED -> RustGrayscaleMode.WEIGHTED
                            GrayscaleMode.MAX -> RustGrayscaleMode.MAX
                            GrayscaleMode.MIN -> RustGrayscaleMode.MIN
                            GrayscaleMode.RED -> RustGrayscaleMode.RED
                            GrayscaleMode.GREEN -> RustGrayscaleMode.GREEN
                            GrayscaleMode.BLUE -> RustGrayscaleMode.BLUE
                        }
                        // 2. 创建 Rust 滤镜对象 (现在它接受 mode 参数了)
                        val rustFilter = RustGrayscaleFilter(rustMode)
                        // 3. 调用底层
                        applyGrayscale(pixels, w, h, rustFilter)
                    }
                    is RemoveNoiseFilter -> {
                        val rustFilter = RustRemoveNoiseFilter(filter.minArea, filter.gap, filter.removeWhite)
                        applyRemoveNoise(pixels, w, h, rustFilter)
                    }
                    is RemoveLinesFilter -> {
                        val rustFilter = RustRemoveLinesFilter(filter.minLength, filter.removeHorizontal, filter.removeVertical)
                        applyRemoveLines(pixels, w, h, rustFilter)
                    }
                    is ExtractContoursFilter -> {
                        val rustFilter = uniffi.touch_core.ExtractContoursFilter(
                            filter.isCanny,
                            filter.cannyLow,
                            filter.cannyHigh,
                            filter.morphKernel.toUByte()
                        )
                        applyExtractContours(pixels, w, h, rustFilter)
                    }
                    is ExtractBlobsFilter -> {
                        val rustFilter = uniffi.touch_core.ExtractBlobsFilter(
                            filter.minWidth.toUInt(),
                            filter.maxWidth.toUInt(),
                            filter.minHeight.toUInt(),
                            filter.maxHeight.toUInt(),
                            filter.minArea.toUInt(),
                            filter.maxArea.toUInt(),
                        )
                        applyExtractBlobs(pixels, w, h, rustFilter)
                    }
                    is DeskewFilter -> {
                        val rustFilter = uniffi.touch_core.DeskewFilter(filter.angle, filter.isAuto,filter.bgColor.toUByte())
                        applyDeskew(pixels, w, h, rustFilter)
                    }
                    is RotationFilter -> {
                        val rustFilter = uniffi.touch_core.RotationFilter(
                            filter.isAuto,
                            filter.angle.toDouble(),
                            filter.maxSearchRange.toDouble(),
                            filter.precision.toDouble()
                        )
                        applyRotate(pixels, w, h, rustFilter)

                    }
                    is BlackWhiteInvertFilter -> {
                        applyBlackwhiteInvert(pixels, w, h, RustBlackWhiteInvertFilter(filter.mode))
                    }
                    is MorphologyFilter -> {
                        val mode = when (filter.mode) {
                            MorphologyMode.DILATE -> RustMorphologyMode.DILATE
                            MorphologyMode.ERODE -> RustMorphologyMode.ERODE
                            MorphologyMode.OPEN -> RustMorphologyMode.OPEN
                            MorphologyMode.CLOSE -> RustMorphologyMode.CLOSE
                            MorphologyMode.GRADIENT -> RustMorphologyMode.GRADIENT
                        }
                        val rustFilter = uniffi.touch_core.MorphologyFilter(mode, filter.kernelSize, filter.iterations)
                        applyMorphologyFilter(pixels, w, h, rustFilter)
                    }
                    is DenoiseFilter -> applyDenoise(pixels, w, h, RustDenoiseFilter(filter.radius))
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