package org.eu.freex.tools.modules.image.application

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eu.freex.tools.common.utils.ImageUtils
import org.eu.freex.tools.modules.image.domain.model.ImageFilter
import org.eu.freex.tools.modules.image.domain.model.RecognitionResult
import org.eu.freex.tools.modules.image.domain.model.SegmentationConfig
import org.eu.freex.tools.modules.image.domain.model.SegmentationRect
import org.eu.freex.tools.modules.image.domain.repository.LayerRepository
import org.eu.freex.tools.modules.image.domain.repository.ProjectRepository
import uniffi.touch_core.RsFontItem
import uniffi.touch_core.rsFontRecognize
import java.awt.image.BufferedImage
import java.io.File

class RecognitionUseCase(
    private val projectRepo: ProjectRepository,
    private val layerRepo: LayerRepository
) {

    suspend fun recognize(
        imageFile: File,
        config: SegmentationConfig,
        filters: List<ImageFilter>,
        minConfidence: Float = 0.7f
    ): Result<List<RecognitionResult>> = withContext(Dispatchers.Default) {
        runCatching {
            // ⏱️ 1. 记录开始时间
            val startTime = System.currentTimeMillis()
            println("\n====== 🚀 开始识别流程追踪 ======")

            // --- 1. 图片加载 ---
            println("📍 [步骤 1/5] 加载原始图片: ${imageFile.absolutePath}")
            if (!imageFile.exists()) throw IllegalStateException("文件不存在")

            val rawImage = layerRepo.loadFromFile(imageFile)
            println("   ✅ 图片加载成功: ${rawImage.width}x${rawImage.height}")

            // --- 2. 预处理 (滤镜) ---
            println("📍 [步骤 2/5] 应用滤镜链 (滤镜数: ${filters.size})")
            filters.forEach { println("      -> 滤镜: ${it::class.simpleName}") }

            val processedImages = layerRepo.applyPipeline(rawImage, filters)
            val finalImage = processedImages.lastOrNull() ?: rawImage

            // 🔍 Debug: 检查处理后的图片是不是变成全黑/全白了
            val debugStats = analyzeImageStats(finalImage)
            println("   ✅ 预处理完成。图像状态: $debugStats")

            // 💡 强烈建议：把处理后的图保存到临时目录看看，确认是不是咱们想的那样
            // saveDebugImage(finalImage, "debug_processed.png")

            // --- 3. 切割 ---
            println("📍 [步骤 3/5] 执行切割 (模式: ${config.mode})")
            val rects = layerRepo.performSegmentation(finalImage, config).getOrThrow()
            println("   ✅ 切割完成。找到切片数量: 【${rects.size}】")

            if (rects.isEmpty()) {
                println("   ❌ 警告：未找到任何切片！流程提前终止。")
                println("      可能原因：1. 固定网格(FixedGrid)参数与当前图片尺寸不符")
                println("      可能原因：2. 投影切割(Projection)阈值过高，导致认为是全白背景")
                return@runCatching emptyList()
            }

            // --- 4. 字库准备 ---
            val fontLibrary = projectRepo.workspace.value.fontLibrary
            println("📍 [步骤 4/5] 加载字库 (字模数: ${fontLibrary.size})")
            if (fontLibrary.isEmpty()) {
                println("   ❌ 警告：字库为空！无法匹配。请先去【字库管理】添加字模。")
                return@runCatching emptyList()
            }

            // 🔥 [重构核心] 将 Kotlin 字库对象转换为 Rust 结构体
            // 这一步确保传递给底层的是包含正确宽高的标准数据
            val rustLibrary = fontLibrary.map {
                RsFontItem(
                    charName = it.charName,
                    binaryData = it.binaryData,
                    width = it.width,
                    height = it.height
                )
            }

            // --- 5. 匹配 ---
            println("📍 [步骤 5/5] 开始特征匹配 (阈值: $minConfidence)")
            val results = mutableListOf<RecognitionResult>()

            // 为了不刷屏，我们只打印前 3 个和后 3 个的详细日志
            rects.forEachIndexed { index, rect ->
                val charImage = cropImage(finalImage, rect) ?: return@forEachIndexed


                // 2. 转像素数据
                val pixels = ImageUtils.toRgbaPixels(charImage)

                // 3. 🔥 调用 Rust 统一接口 (替代原本的 findBestMatch)
                // Rust 内部会自动执行：Resize -> Feature Extract -> Similarity
                val rustResult = rsFontRecognize(
                    pixels = pixels,
                    width = charImage.width,
                    height = charImage.height,
                    library = rustLibrary,
                    minConfidence = minConfidence
                )
                // 4. 处理结果
                val score = rustResult.confidence
                val charName = rustResult.charName

                // 日志 (保留部分调试信息)
                if (index < 5 && score > 0) {
                    println("      🔎 切片 #$index [${rect.width}x${rect.height}] -> 匹配: '$charName' ($score)")
                }

                if (score >= minConfidence && charName.isNotEmpty()) {
                    results.add(
                        RecognitionResult(
                            char = charName,
                            rect = rect,
                            confidence = score
                        )
                    )
                }
            }

            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime

            println("====== 🏁 流程结束。总耗时: $duration ms | 识别出: ${results.size} 个字符 ======\n")
            results
        }
    }

    // --- 辅助工具 ---

    private fun analyzeImageStats(image: BufferedImage): String {
        var total = 0L
        val w = image.width
        val h = image.height
        for (x in 0 until w) {
            for (y in 0 until h) {
                total += (image.getRGB(x, y) and 0xFF) // 取蓝色通道近似亮度
            }
        }
        val avg = total / (w * h)
        return "尺寸=${w}x${h}, 平均亮度=$avg (0=全黑, 255=全白)"
    }

    private fun cropImage(source: BufferedImage, rect: SegmentationRect): BufferedImage? {
        val w = rect.width.toInt()
        val h = rect.height.toInt()
        if (w <= 0 || h <= 0) return null
        if (rect.left < 0 || rect.top < 0 || rect.left + w > source.width || rect.top + h > source.height) return null
        val subImage = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = subImage.createGraphics()
        g.drawImage(source, -rect.left, -rect.top, null)
        g.dispose()
        return subImage
    }
}