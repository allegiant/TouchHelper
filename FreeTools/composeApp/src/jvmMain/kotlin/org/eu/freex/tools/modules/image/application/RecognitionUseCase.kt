package org.eu.freex.tools.modules.image.application

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eu.freex.tools.common.utils.ImageFeatureExtractor
import org.eu.freex.tools.modules.image.domain.model.*
import org.eu.freex.tools.modules.image.domain.repository.LayerRepository
import org.eu.freex.tools.modules.image.domain.repository.ProjectRepository
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

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
                ?: throw IllegalStateException("图片加载失败，对象为空")
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

            // --- 5. 匹配 ---
            println("📍 [步骤 5/5] 开始特征匹配 (阈值: $minConfidence)")
            val results = mutableListOf<RecognitionResult>()

            // 为了不刷屏，我们只打印前 3 个和后 3 个的详细日志
            rects.forEachIndexed { index, rect ->
                val charImage = cropImage(finalImage, rect)
                if (charImage == null) {
                    println("      ⚠️ [$index] 切片无效，跳过")
                    return@forEachIndexed
                }


                // 寻找匹配
                val bestMatch = findBestMatch(charImage, fontLibrary)
                val score = bestMatch?.second ?: 0f
                val charName = bestMatch?.first?.charName ?: "无"

                // 打印详细日志 (仅打印前5个，方便调试)
                if (index < 5) {
                    println("      🔎 切片 #$index [${rect.width}x${rect.height}] -> 最佳匹配: '$charName' (分值: $score)")
                }

                if (score >= minConfidence && bestMatch != null) {
                    results.add(RecognitionResult(
                        char = charName,
                        rect = rect,
                        confidence = score
                    ))
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

    // 开发调试用：保存中间图片到用户目录
    private fun saveDebugImage(image: BufferedImage, name: String) {
        try {
            val file = File(System.getProperty("user.home"), name)
            ImageIO.write(image, "png", file)
            println("      💾 [DEBUG] 中间图已保存至: ${file.absolutePath}")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun cropImage(source: BufferedImage, rect: SegmentationRect): BufferedImage? {
        val w = rect.width.toInt()
        val h = rect.height.toInt()
        if (w <= 0 || h <= 0) return null
        if (rect.left < 0 || rect.top < 0 || rect.left + w > source.width || rect.top + h > source.height) return null
        val subImage = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = subImage.createGraphics()
        g.drawImage(source, -rect.left.toInt(), -rect.top.toInt(), null)
        g.dispose()
        return subImage
    }

    private fun findBestMatch(
        // 注意：这里不再传入 targetBinary，而是传入 targetImage，因为我们要动态缩放它
        targetImage: BufferedImage,
        library: List<FontLibItem>
    ): Pair<FontLibItem, Float>? {
        var bestItem: FontLibItem? = null
        var maxScore = 0f

        for (item in library) {
            // 1. 宽高比检查 (可选)
            // 如果一个字是很扁的，而字库里是很瘦的，强行缩放也没意义，可以直接跳过
            // 这里暂且不做严格限制，让它尽量匹配

            // 2. 🌟 关键步骤：将【切出来的图】缩放到【字库样本】的大小
            // 比如：切出来是 86x61，字库是 40x30 --> 把切出来的图缩成 40x30
            val resizedTarget = resizeImage(targetImage, item.width, item.height)

            // 3. 生成特征 (此时尺寸完全一致了)
            val dynamicBinary = ImageFeatureExtractor.generateBinaryData(resizedTarget)

            // 4. 比对
            val score = calculateSimilarity(
                dynamicBinary, item.width, item.height,
                item.binaryData, item.width, item.height
            )

            if (score > maxScore) {
                maxScore = score
                bestItem = item
            }
        }

        return if (bestItem != null) bestItem to maxScore else null
    }

    private fun calculateSimilarity(bin1: String, w1: Int, h1: Int, bin2: String, w2: Int, h2: Int): Float {
        val minW = kotlin.math.min(w1, w2)
        val minH = kotlin.math.min(h1, h2)
        var matchCount = 0
        var totalPixels = 0
        for (y in 0 until minH) {
            for (x in 0 until minW) {
                val idx1 = y * w1 + x
                val idx2 = y * w2 + x
                if (idx1 < bin1.length && idx2 < bin2.length) {
                    if (bin1[idx1] == bin2[idx2]) matchCount++
                    totalPixels++
                }
            }
        }
        return if (totalPixels > 0) matchCount.toFloat() / totalPixels else 0f
    }
    /**
     * 将图片强制缩放到指定尺寸 (橡皮泥模式)
     * 用于将切出来的字缩放到和字库样本一样大
     */
    private fun resizeImage(original: BufferedImage, targetW: Int, targetH: Int): BufferedImage {
        val resized = BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB)
        val g = resized.createGraphics()
        // 使用双线性插值，效果较好；如果要追求极致锐利（像素风），可用 KEY_INTERPOLATION_NEAREST_NEIGHBOR
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(original, 0, 0, targetW, targetH, null)
        g.dispose()
        return resized
    }
}