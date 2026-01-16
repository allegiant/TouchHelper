package org.eu.freex.tools.modules.image.application

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.eu.freex.tools.modules.image.data.repository.LayerRepositoryImpl
import org.eu.freex.tools.modules.image.domain.model.FontLibItem
import org.eu.freex.tools.modules.image.domain.model.ImageFilter
import org.eu.freex.tools.modules.image.domain.model.ImageLayer
import org.eu.freex.tools.modules.image.domain.model.ImageWorkspace
import org.eu.freex.tools.modules.image.domain.model.LayerConfig
import org.eu.freex.tools.modules.image.domain.model.Pipeline
import org.eu.freex.tools.modules.image.domain.model.SegmentationConfig
import org.eu.freex.tools.modules.image.domain.model.SegmentationRect
import org.eu.freex.tools.modules.image.domain.repository.LayerRepository
import java.awt.image.BufferedImage
import java.io.File

class WorkspaceUseCase(
    private val repository: LayerRepository
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    // --- 资源 ---
    suspend fun importAsset(file: File): Result<ImageLayer> = runCatching {
        val image = repository.loadFromFile(file)
        ImageLayer(
            name = file.name,
            image = image,
            config = LayerConfig.Origin(file.absolutePath)
        )
    }

    suspend fun exportImage(layer: ImageLayer, file: File): Result<Unit> = runCatching {
        val image = layer.image ?: throw IllegalStateException("Empty image")
        repository.saveToFile(image, file)
    }

    fun removeAsset(workspace: ImageWorkspace, assetId: String): ImageWorkspace {
        val newAssets = workspace.assets.filter { it.id != assetId }
        var newChain = workspace.pipeline
        if (newChain?.inputAssetId == assetId) {
            val nextAsset = newAssets.firstOrNull()
            newChain = if (nextAsset != null) {
                Pipeline(inputAssetId = nextAsset.id, activeIndex = -1)
            } else {
                null
            }
        }
        return workspace.copy(
            assets = newAssets,
            pipeline = newChain,
        )
    }

    // --- 截图 (修改后：逻辑下沉，只负责调用) ---
    suspend fun captureScreen(): Result<ImageLayer> = runCatching {
        // 调用 Repository 的截图方法
        // 具体的“隐藏窗口”、“计算多屏幕”、“Robot调用”全部在 RepositoryImpl 里实现
        val image = repository.captureScreen()

        ImageLayer(
            name = "Capture_${System.currentTimeMillis()}",
            image = image, // 这里的 image 在 Desktop 上是 BufferedImage，在 Android 上是 Bitmap
            config = LayerConfig.Origin("mem")
        )
    }

    // --- 裁剪 (逻辑简化：直接信任传入的物理坐标) ---
    suspend fun cropImage(sourceLayer: ImageLayer, cropRect: Rect): Result<ImageLayer> = runCatching {
        val source = sourceLayer.image ?: throw IllegalStateException("No source image")

        // 移除之前的 GraphicsEnvironment/Scale 计算逻辑
        // 因为 ScreenCropperDialog 现在已经根据 View/Bitmap 比例计算好了真实的物理坐标

        // 越界检查 (使用 Rect 的 intersect 方法来确保安全区域)
        val imageBounds = Rect(0f, 0f, source.width.toFloat(), source.height.toFloat())
        // 取交集，确保 cropRect 不超出图片范围
        val safeRect = cropRect.intersect(imageBounds)

        if (safeRect.isEmpty) {
            throw IllegalStateException("Invalid crop area: $safeRect")
        }

        val cropped = repository.crop(source, safeRect)
        ImageLayer(
            name = "Crop_${sourceLayer.name}",
            image = cropped,
            config = LayerConfig.Origin("cropped")
        )
    }

    // 修改：改为 suspend，因为需要重新跑滤镜
    // 逻辑：保留当前滤镜链的 Config，只替换 Input Source，并重新计算所有步骤
    suspend fun activateAsset(workspace: ImageWorkspace, assetId: String): Result<ImageWorkspace> = runCatching {
        if (workspace.pipeline?.inputAssetId == assetId) return@runCatching workspace

        val newBaseLayer = workspace.assets.find { it.id == assetId }
            ?: throw IllegalStateException("Asset not found")
        val baseImage = newBaseLayer.image
            ?: throw IllegalStateException("Base image is empty")

        val oldSteps = workspace.pipeline?.steps ?: emptyList()

        // 1. 提取所有滤镜配置
        val filtersToApply = oldSteps.mapNotNull { (it.config as? LayerConfig.Filter)?.filter }

        // 2. [优化] 调用 Rust 批量处理
        // 此时 baseImage 只上传一次，所有滤镜在 Rust 内部连续执行
        val resultImages = if (filtersToApply.isNotEmpty()) {
            // 注意：这里需要强转 Repository 类型，或者在接口里定义该方法
            repository.applyPipeline(baseImage, filtersToApply)
        } else {
            emptyList()
        }

        // 3. 组装新的 Steps
        // 我们利用 resultImages 的下标与 oldSteps 对应
        var resultIndex = 0
        val newSteps = oldSteps.map { step ->
            if (step.config is LayerConfig.Filter) {
                val newImage = resultImages[resultIndex++]
                step.copy(image = newImage, config = LayerConfig.Filter(step.config.filter))
            } else {
                step // 非滤镜层保持原样
            }
        }

        val newChain = Pipeline(
            inputAssetId = assetId,
            steps = newSteps,
            activeIndex = if (newSteps.isNotEmpty()) newSteps.lastIndex else -1
        )

        workspace.copy(pipeline = newChain)
    }


    suspend fun addFilterStep(workspace: ImageWorkspace, filter: ImageFilter): Result<ImageWorkspace> = runCatching {
        val chain = workspace.pipeline ?: throw IllegalStateException("No active image")
        val inputLayer = chain.getActiveLayer(workspace.assets) ?: throw IllegalStateException("No input")
        val inputImage = inputLayer.image ?: throw IllegalStateException("No data")

        val result = repository.applyFilter(inputImage, filter)
        val newLayer = ImageLayer(
            name = filter.name,
            image = result,
            config = LayerConfig.Filter(filter)
        )

        val currentSteps = if (chain.activeIndex == -1) emptyList() else chain.steps.take(chain.activeIndex + 1)
        val newSteps = currentSteps + newLayer
        workspace.copy(pipeline = chain.copy(steps = newSteps, activeIndex = newSteps.lastIndex))
    }

    // 修改 updateFilterStep 方法
    suspend fun updateFilterStep(workspace: ImageWorkspace, filter: ImageFilter): Result<ImageWorkspace> = runCatching {
        val chain = workspace.pipeline ?: throw IllegalStateException("No active chain")
        val activeIndex = chain.activeIndex
        if (activeIndex == -1) throw IllegalStateException("Cannot modify origin layer")

        // 1. 确定“重算起点”的输入图
        // 如果改的是第 N 步，那么第 N-1 步的结果就是本次批处理的“底图”
        val baseInputLayer = if (activeIndex == 0) {
            workspace.assets.find { it.id == chain.inputAssetId }
        } else {
            chain.steps.getOrNull(activeIndex - 1)
        } ?: throw IllegalStateException("Base input layer not found")

        val baseImage = baseInputLayer.image ?: throw IllegalStateException("Base image data missing")

        // 2. 准备从 activeIndex 开始的所有滤镜
        // 注意：当前这一步(activeIndex) 使用传入的新 filter，后续步骤使用原本的 filter
        val filtersToApply = mutableListOf<ImageFilter>()
        filtersToApply.add(filter) // 当前这步的新滤镜

        for (i in (activeIndex + 1) until chain.steps.size) {
            val nextStepConfig = chain.steps[i].config as? LayerConfig.Filter
            if (nextStepConfig != null) {
                filtersToApply.add(nextStepConfig.filter)
            }
        }

        // 3. [优化] 调用 Rust 批量处理
        val resultImages = (repository as LayerRepositoryImpl).applyPipeline(baseImage, filtersToApply)

        // 4. 更新 Steps 列表
        val newSteps = chain.steps.toMutableList()

        // 回填结果
        // i 是 steps 中的绝对下标，j 是 resultImages 中的相对下标
        for ((j, resultImage) in resultImages.withIndex()) {
            val stepIndex = activeIndex + j
            val targetFilter = filtersToApply[j]

            val oldLayer = newSteps[stepIndex]
            newSteps[stepIndex] = oldLayer.copy(
                name = targetFilter.name,
                image = resultImage,
                config = LayerConfig.Filter(targetFilter)
            )
        }

        workspace.copy(pipeline = chain.copy(steps = newSteps))
    }

    suspend fun calculatePreview(inputLayer: ImageLayer, filter: ImageFilter): ImageLayer? {
        val inputImage = inputLayer.image ?: return null
        val result = repository.applyFilter(inputImage, filter)
        return ImageLayer(
            name = "Preview",
            image = result,
            config = LayerConfig.Filter(filter)
        )
    }

    // --- 持久化 (修复：确保恢复所有 Asset 的图片数据) ---
    suspend fun saveWorkspace(file: File, workspace: ImageWorkspace): Result<Unit> = runCatching {
        // 在保存前可以做一些清理，比如只保存有路径的资源
        val data = json.encodeToString(workspace)
        withContext(Dispatchers.IO) {
            file.writeText(data)
        }
    }

    // WorkspaceUseCase.kt

    // --- 载入工程 (针对单流水线设计的恢复逻辑) ---
    suspend fun loadWorkspace(file: File): Result<ImageWorkspace> = runCatching {
        val jsonText = withContext(Dispatchers.IO) { file.readText() }
        val workspace = json.decodeFromString<ImageWorkspace>(jsonText)

        // 1. 恢复所有资源图
        val restoredAssets = workspace.assets.map { layer ->
            val config = layer.config
            if (config is LayerConfig.Origin && config.sourcePath != "mem") {
                val imgFile = File(config.sourcePath)
                if (imgFile.exists()) {
                    layer.copy(image = repository.loadFromFile(imgFile))
                } else layer
            } else layer
        }

        // 2. 恢复唯一的流水线图片数据
        val oldChain = workspace.pipeline
        val restoredActiveChain = if (oldChain != null) {
            // 找到流水线指定的输入源图片
            val baseLayer = restoredAssets.find { it.id == oldChain.inputAssetId }
            var currentImage = baseLayer?.image

            if (currentImage != null) {
                // 拿着这套滤镜参数，重新生成每一步的预览图
                val restoredSteps = oldChain.steps.map { step ->
                    if (step.config is LayerConfig.Filter) {
                        val resultImage = repository.applyFilter(currentImage!!, step.config.filter)
                        currentImage = resultImage
                        step.copy(image = resultImage)
                    } else step
                }
                oldChain.copy(steps = restoredSteps)
            } else oldChain
        } else null

        // 3.  恢复字库预览图 (从 binaryData 重建 ImageBitmap)
        val restoredLibrary = workspace.fontLibrary.map { item ->
            if (item.displayBitmap == null && item.binaryData.isNotEmpty()) {
                // 调用下方的重建函数
                val bitmap = reconstructBitmapFromBinary(item.binaryData, item.width, item.height)
                item.copy(displayBitmap = bitmap)
            } else {
                item
            }
        }

        workspace.copy(
            assets = restoredAssets,
            pipeline = restoredActiveChain,
            fontLibrary = restoredLibrary
        )
    }

    suspend fun performSegmentation(
        image: BufferedImage,
        config: SegmentationConfig
    ): Result<List<SegmentationRect>> {
        return repository.performSegmentation(image, config)
    }

    // 修改 removeStep 方法
    suspend fun removeStep(workspace: ImageWorkspace, index: Int): Result<ImageWorkspace> = runCatching {
        val chain = workspace.pipeline ?: throw IllegalStateException("No active chain")
        if (index !in chain.steps.indices) throw IllegalArgumentException("Invalid index")

        // 1. 确定“重算起点”
        // 删除的是 index，那么 index-1 的结果不受影响，作为新的底图
        val baseInputLayer = if (index == 0) {
            workspace.assets.find { it.id == chain.inputAssetId }
        } else {
            chain.steps[index - 1]
        } ?: throw IllegalStateException("Base input layer not found")

        val baseImage = baseInputLayer.image ?: throw IllegalStateException("Base image missing")

        // 2. 准备需要保留的后续滤镜
        // 从 index+1 开始的所有滤镜都需要重算
        val filtersToApply = mutableListOf<ImageFilter>()
        for (i in (index + 1) until chain.steps.size) {
            val cfg = chain.steps[i].config as? LayerConfig.Filter
            if (cfg != null) filtersToApply.add(cfg.filter)
        }

        // 3. [优化] 调用 Rust 批量处理
        // 如果后面没有步骤了(删除的是最后一步)，applyPipeline 会直接返回 emptyList，逻辑也是对的
        val resultImages = (repository as LayerRepositoryImpl).applyPipeline(baseImage, filtersToApply)

        // 4. 构建新列表
        val newSteps = chain.steps.toMutableList()
        newSteps.removeAt(index) // 先删掉目标

        // 回填后续结果
        // 现在的 newSteps[index] 其实就是原来的 steps[index+1]
        for ((j, resultImage) in resultImages.withIndex()) {
            val stepIndex = index + j
            val oldLayer = newSteps[stepIndex]
            newSteps[stepIndex] = oldLayer.copy(image = resultImage)
        }

        // 5. 修正 activeIndex (保持原逻辑)
        val currentActive = chain.activeIndex
        val newActiveIndex = when {
            newSteps.isEmpty() -> -1
            currentActive == index -> (index - 1).coerceAtLeast(if (newSteps.isNotEmpty()) 0 else -1)
            currentActive > index -> currentActive - 1
            else -> currentActive
        }

        workspace.copy(pipeline = chain.copy(steps = newSteps, activeIndex = newActiveIndex))
    }

    /**
     * 辅助方法：将 01 字符串转换为 Compose ImageBitmap (用于预览)
     */
    private fun reconstructBitmapFromBinary(binary: String, width: Int, height: Int): androidx.compose.ui.graphics.ImageBitmap {
        val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

        // 限制长度防止越界
        val length = minOf(binary.length, width * height)

        // 定义颜色 (使用 java.awt.Color 常量更安全)
        val colorRed = java.awt.Color.RED.rgb      // 前景：红色
        val colorTransparent = 0x00000000          // 背景：全透明

        for (i in 0 until length) {
            val x = i % width
            val y = i / width
            val char = binary[i]

            if (char == '1') {
                bufferedImage.setRGB(x, y, colorRed)
            } else {
                bufferedImage.setRGB(x, y, colorTransparent)
            }
        }
        return bufferedImage.toComposeImageBitmap()
    }

    /**
     * 【修复版】批量添加 + 智能二值化 (自动识别白字/黑字)
     */
    suspend fun addBatchToLibrary(workspace: ImageWorkspace,items: List<Pair<SegmentationRect, String>>, sourceImage: BufferedImage): Result<ImageWorkspace> = runCatching{
        val newLibItems = ArrayList<FontLibItem>()

        for ((rect, label) in items) {
            // 1. 检查参数有效性
            if (label.isBlank()) continue
            val w = rect.width.toInt()
            val h = rect.height.toInt()
            if (w <= 0 || h <= 0) continue

            try {
                // 2. 安全绘图 (Graphics2D)
                val subImage = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
                val g = subImage.createGraphics()
                // 坐标取整并取反，实现裁剪
                val drawX = -rect.left.toInt()
                val drawY = -rect.top.toInt()
                g.drawImage(sourceImage, drawX, drawY, null)
                g.dispose()

                // 3. 智能二值化 (Smart Binarization)
                // 先统计平均亮度，判断是"白底黑字"还是"黑底白字"
                var totalBrightness = 0L
                var pixelCount = 0
                for (y in 0 until subImage.height) {
                    for (x in 0 until subImage.width) {
                        val pixel = subImage.getRGB(x, y)
                        val r = (pixel shr 16) and 0xff
                        val gVal = (pixel shr 8) and 0xff
                        val b = pixel and 0xff
                        // 亮度公式
                        totalBrightness += (r * 0.299 + gVal * 0.587 + b * 0.114).toLong()
                        pixelCount++
                    }
                }

                // 默认为128，防止除以0
                val avgBrightness = if (pixelCount > 0) totalBrightness / pixelCount else 128

                // 如果平均亮度较低(<128)，说明背景是黑的，文字是亮的 -> 阈值条件：像素 > 平均值
                // 如果平均亮度较高(>128)，说明背景是白的，文字是暗的 -> 阈值条件：像素 < 平均值
                val isDarkBackground = avgBrightness < 128

                val binaryData = StringBuilder()
                var oneCount = 0

                for (y in 0 until subImage.height) {
                    for (x in 0 until subImage.width) {
                        val pixel = subImage.getRGB(x, y)
                        val alpha = (pixel shr 24) and 0xff
                        val r = (pixel shr 16) and 0xff
                        val gVal = (pixel shr 8) and 0xff
                        val b = pixel and 0xff
                        val luma = (r * 0.299 + gVal * 0.587 + b * 0.114).toInt()

                        // 判断是否为前景(1)
                        // 逻辑：必须不透明(alpha>50)，且亮度符合前景特征
                        val isForeground = if (alpha < 50) {
                            false
                        } else if (isDarkBackground) {
                            luma > avgBrightness + 20 // 黑底：比背景亮的是字
                        } else {
                            luma < avgBrightness - 20 // 白底：比背景暗的是字
                        }

                        if (isForeground) {
                            binaryData.append("1")
                            oneCount++
                        } else {
                            binaryData.append("0")
                        }
                    }
                }

                // 如果全是0，打印警告
                if (oneCount == 0) {
                    println("Warning: Char '$label' produced empty binary data! (AvgLuma: $avgBrightness)")
                }

                // 4. 构建对象
                newLibItems.add(
                    FontLibItem(
                        charName = label,
                        width = subImage.width,
                        height = subImage.height,
                        binaryData = binaryData.toString(),
                        displayBitmap = subImage.toComposeImageBitmap() // 刚添加时直接用原图显示
                    )
                )
            } catch (e: Exception) {
                println("Error processing char '$label': ${e.message}")
                e.printStackTrace()
            }
        }
        workspace.copy(fontLibrary = workspace.fontLibrary + newLibItems)
    }

    suspend fun exportFontLibrary(workspace: ImageWorkspace, file: File): Result<Unit> = runCatching {
        val content = StringBuilder()
        workspace.fontLibrary.forEach { item ->
            content.append("${item.charName}$${item.width}$${item.height}$${item.binaryData}\n")
        }
        file.writeText(content.toString())
    }
}