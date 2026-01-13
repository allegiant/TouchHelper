package org.eu.freex.tools.modules.image.data.repository

import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.eu.freex.tools.common.utils.ImageUtils
import org.eu.freex.tools.modules.image.domain.model.*
import org.eu.freex.tools.modules.image.domain.repository.LayerRepository
import uniffi.touch_core.applyAutoCrop
import java.awt.image.BufferedImage
import java.io.File
import kotlin.math.roundToInt

// Rust Bindings
import uniffi.touch_core.applyBinarization
import uniffi.touch_core.applyBlackwhiteInvert
import uniffi.touch_core.applyDenoise
import uniffi.touch_core.applyDeskew
import uniffi.touch_core.applyExtendCrop
import uniffi.touch_core.applyExtractBlobs
import uniffi.touch_core.applyExtractContours
import uniffi.touch_core.applyGrayscale
import uniffi.touch_core.applyMorphologyFilter
import uniffi.touch_core.applyMultiColorFilter
import uniffi.touch_core.applyPosterizationFilter
import uniffi.touch_core.applyRemoveLines
import uniffi.touch_core.applyRemoveNoise
import uniffi.touch_core.applyResizeScale
import uniffi.touch_core.applyRotate
import uniffi.touch_core.applySmartLayout
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
import uniffi.touch_core.AutoCropMode as RustAutoCropMode

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
                            filter.useEightConnectivity
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
                    is SmartLayoutFilter -> {
                        val rustFilter = uniffi.touch_core.SmartLayoutFilter(
                            padding = filter.padding,
                            minWidth = filter.minWidth,
                            minHeight = filter.minHeight,
                            fixedHeight = filter.fixedHeight,
                            alignCenter = filter.alignCenter
                        )
                        val result = applySmartLayout(pixels, w, h, rustFilter)

                        // 使用新的宽高创建图片，解决花屏问题
                        return@withContext ImageUtils.fromRgbaPixels(
                            result.width,
                            result.height,
                            result.pixels
                        )
                    }
                    is AutoCropFilter -> {
                        val mode = when (filter.mode) {
                            AutoCropMode.AUTO_CORNERS -> RustAutoCropMode.AUTO_CORNERS
                            AutoCropMode.FIXED_COLOR -> RustAutoCropMode.FIXED_COLOR
                        }


                        val rustFilter = uniffi.touch_core.AutoCropFilter(
                            mode = mode,
                            tolerance = filter.tolerance,
                            padding = filter.padding,
                            noiseThreshold = filter.noiseThreshold,
                            fixedColorHex = filter.fixedColorHex
                        )
                        val result = applyAutoCrop(pixels,w,h,rustFilter)
                        return@withContext ImageUtils.fromRgbaPixels(
                            result.width,
                            result.height,
                            result.pixels
                        )
                    }
                    is ResizeScaleFilter -> {
                        val rustFilter = uniffi.touch_core.ResizeScaleFilter(filter.scaleFactor, filter.highQuality)
                        val result = applyResizeScale(pixels, w, h, rustFilter)
                        return@withContext ImageUtils.fromRgbaPixels(
                            result.width,
                            result.height,
                            result.pixels
                        )
                    }
                    is ExtendCropFilter -> {
                        val rustFilter = uniffi.touch_core.ExtendCropFilter(filter.x1,filter.y1, filter.x2,filter.y2)
                        val result = applyExtendCrop(pixels, w, h, rustFilter)
                        return@withContext ImageUtils.fromRgbaPixels(
                            result.width,
                            result.height,
                            result.pixels
                        )
                    }
                    is DenoiseFilter -> applyDenoise(pixels, w, h, RustDenoiseFilter(filter.radius))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext source
            }
            ImageUtils.fromRgbaPixels(w, h, resultPixels)
        }

    override suspend fun crop(source: BufferedImage, rect: Rect): BufferedImage = withContext(Dispatchers.Default) {
        ImageUtils.cropImage(source, rect)
    }

    override suspend fun performSegmentation(
        image: BufferedImage,
        config: SegmentationConfig
    ): Result<List<SegmentationRect>> = withContext(Dispatchers.Default) {
        runCatching {
            // 1. 图像转换: BufferedImage -> RGBA ByteArray
            val width = image.width
            val height = image.height
            val pixels = IntArray(width * height)
            // 获取 ARGB 数据
            image.getRGB(0, 0, width, height, pixels, 0, width)

            // [修正] 使用 ByteArray 而不是 ArrayList<Byte>
            val byteArray = ByteArray(width * height * 4)
            var index = 0

            for (argb in pixels) {
                // Java getRGB 返回 ARGB，我们需要 RGBA
                byteArray[index++] = ((argb shr 16) and 0xFF).toByte() // R
                byteArray[index++] = ((argb shr 8) and 0xFF).toByte()  // G
                byteArray[index++] = (argb and 0xFF).toByte()          // B
                byteArray[index++] = ((argb shr 24) and 0xFF).toByte() // A
            }

            // 2. 配置转换: Domain Config -> Rust Config
            val rustMode = when (config.mode) {
                SegmentationMode.FIXED_GRID -> uniffi.touch_core.SegmentationMode.FIXED_GRID
                SegmentationMode.PROJECTION -> uniffi.touch_core.SegmentationMode.PROJECTION
                SegmentationMode.CONNECTED_COMP -> uniffi.touch_core.SegmentationMode.CONNECTED_COMP
            }

            val rustConfig = uniffi.touch_core.SegmentationConfig(
                mode = rustMode,
                padding = config.padding,
                minWidth = config.minWidth,
                minHeight = config.minHeight,
                maxWidth = config.maxWidth,
                maxHeight = config.maxHeight,
                mergeDistance = config.mergeDistance,
                startX = config.startX,
                startY = config.startY,
                cellWidth = config.cellWidth,
                cellHeight = config.cellHeight,
                colCount = config.colCount,
                rowCount = config.rowCount,
                colGap = config.colGap,
                rowGap = config.rowGap,
                splitRows = config.splitRows,
                splitCols = config.splitCols,
                projectionThreshold = config.projectionThreshold
            )

            // 3. 调用 Rust 接口
            // 现在 byteArray 的类型是 ByteArray，符合 UniFFI 生成代码的要求
            val rustRects = uniffi.touch_core.performSegmentation(byteArray, width, height, rustConfig)

            // 4. 结果转换: Rust Rects -> Domain Rects
            rustRects.map { r ->
                SegmentationRect(
                    left = r.left,
                    top = r.top,
                    width = r.width,
                    height = r.height
                )
            }
        }
    }
}