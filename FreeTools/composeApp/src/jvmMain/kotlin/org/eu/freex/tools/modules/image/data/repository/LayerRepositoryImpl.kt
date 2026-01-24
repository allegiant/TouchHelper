package org.eu.freex.tools.modules.image.data.repository

import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eu.freex.tools.common.utils.ImageUtils
import org.eu.freex.tools.modules.image.domain.model.ImageFilter
import org.eu.freex.tools.modules.image.domain.model.SegmentationConfig
import org.eu.freex.tools.modules.image.domain.model.SegmentationMode
import org.eu.freex.tools.modules.image.domain.model.SegmentationRect
import org.eu.freex.tools.modules.image.domain.model.ViewFilter
import org.eu.freex.tools.modules.image.domain.repository.LayerRepository
import org.eu.freex.tools.platform.ScreenCaptureService
import uniffi.touch_core.ImageSession
import java.awt.image.BufferedImage
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class LayerRepositoryImpl(
    private val captureService: ScreenCaptureService
) : LayerRepository {

    override suspend fun loadFromFile(file: File): BufferedImage = withContext(Dispatchers.IO) {
        ImageUtils.read(file) ?: throw Exception("Read failed: ${file.name}")
    }

    override suspend fun saveToFile(image: BufferedImage, file: File) = withContext(Dispatchers.IO) {
        ImageUtils.save(image, file)
    }

    override suspend fun applyFilter(source: BufferedImage, filter: ImageFilter): BufferedImage =
        withContext(Dispatchers.Default) {

            // 1. 特殊处理 ViewFilter (它不需要 Rust 处理)
            if (filter is ViewFilter) {
                return@withContext source
            }
            try {
                val pixels = ImageUtils.toRgbaPixels(source)
                val w = source.width
                val h = source.height
                val rustFilterWrapper = filter.toRust()

                val session = ImageSession(pixels, w, h)
                session.applyFilter(rustFilterWrapper)
                val result = session.getImage()
                // 5. 重建图片
                // 使用 result.width 和 result.height，这样能正确处理 Resize/Crop 等改变尺寸的滤镜
                ImageUtils.fromRgbaPixels(
                    result.width,
                    result.height,
                    result.pixels // 这里是 ByteArray，类型匹配正确
                )
            } catch (e: Exception) {
                e.printStackTrace()
                // 出错时返回原图
                return@withContext source
            }
        }

    /**
     * 批处理 Pipeline
     * 输入: 基础底图 + 滤镜列表
     * 输出: 每一步处理后的图片列表 (List<BufferedImage>)
     */
    override suspend fun applyPipeline(
        baseImage: BufferedImage,
        filters: List<ImageFilter>
    ): List<BufferedImage> = withContext(Dispatchers.Default) {
        if (filters.isEmpty()) return@withContext emptyList()

        val results = mutableListOf<BufferedImage>()

        // 1. 准备数据 (IntArray -> BGRA ByteBuffer)
        val w = baseImage.width
        val h = baseImage.height
        val pixels = IntArray(w * h)
        baseImage.getRGB(0, 0, w, h, pixels, 0, w)

        val byteBuffer = ByteBuffer.allocate(pixels.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        byteBuffer.asIntBuffer().put(pixels)
        val byteArray = byteBuffer.array()

        // 2. 创建 Rust Session
        // ImageSession 会接管 byteArray，在 Rust 内存中驻留
        val session = ImageSession(byteArray, w, h)

        try {
            // 3. 依次应用滤镜，并收集每一步的结果
            filters.forEach { filter ->
                val rustFilter = filter.toRust()
                session.applyFilter(rustFilter)

                // 导出这一步的结果 (Native -> JVM)
                // 虽然这里有一次拷贝，但省去了 "JVM -> Native" 的上传拷贝和解码开销
                val resultData = session.getImage()
                val resultImage = ImageUtils.fromRgbaPixels(
                    resultData.width,
                    resultData.height,
                    resultData.pixels
                )
                results.add(resultImage)
            }
        } finally {
            // Session 自动释放或依靠 GC
        }

        return@withContext results
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

            // 创建 ByteBuffer
            val byteBuffer = ByteBuffer.allocate(pixels.size * 4)
                .order(ByteOrder.LITTLE_ENDIAN) // 设为 Little Endian 以便直接映射 BGRA
            byteBuffer.asIntBuffer().put(pixels)
            val byteArray = byteBuffer.array()


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

            val imageSession = ImageSession(pixels = byteArray, width, height)
            val rustRects = imageSession.segmentation(rustConfig)

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

    override suspend fun captureScreen(): BufferedImage {
        return captureService.captureFullscreen()
    }


    override suspend fun crop(source: BufferedImage, rect: Rect): BufferedImage = withContext(Dispatchers.Default) {
        ImageUtils.cropImage(source, rect)
    }

}