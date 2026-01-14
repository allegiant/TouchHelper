package org.eu.freex.tools.modules.image.application

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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
        // 1. 如果切的是同一张图，直接返回
        if (workspace.pipeline?.inputAssetId == assetId) return@runCatching workspace

        // 2. 找到新的底图
        val newBaseLayer = workspace.assets.find { it.id == assetId }
            ?: throw IllegalStateException("Asset not found")

        var currentImage = newBaseLayer.image
            ?: throw IllegalStateException("Base image is empty")

        // 3. 获取旧的滤镜链配置
        val oldSteps = workspace.pipeline?.steps ?: emptyList()
        val newSteps = mutableListOf<ImageLayer>()

        // 4. 在 IO 线程中重新计算流水线
        withContext(Dispatchers.Default) {
            for (step in oldSteps) {
                // 只迁移滤镜类型的步骤
                if (step.config is LayerConfig.Filter) {
                    val filter = step.config.filter
                    // 对新图应用滤镜
                    val resultImage = repository.applyFilter(currentImage, filter)

                    // 生成新层（保留原有配置）
                    newSteps.add(
                        step.copy(
                            image = resultImage,
                            config = LayerConfig.Filter(filter)
                        )
                    )
                    // 传递给下一步
                    currentImage = resultImage
                }
            }
        }

        // 5. 构建新的 Chain
        val newChain = Pipeline(
            inputAssetId = assetId,
            steps = newSteps,
            // 保持选中最后一步，或者如果原来就在中间，这里简化为选中最后一步
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

    suspend fun updateFilterStep(workspace: ImageWorkspace, filter: ImageFilter): Result<ImageWorkspace> = runCatching {
        val chain = workspace.pipeline ?: throw IllegalStateException("No active chain")
        val activeIndex = chain.activeIndex

        if (activeIndex == -1) throw IllegalStateException("Cannot modify origin layer")

        val firstInputLayer = if (activeIndex == 0) {
            workspace.assets.find { it.id == chain.inputAssetId }
        } else {
            chain.steps.getOrNull(activeIndex - 1)
        } ?: throw IllegalStateException("Base input layer not found")

        var currentImage = firstInputLayer.image ?: throw IllegalStateException("Base image data missing")
        val newSteps = chain.steps.toMutableList()

        for (i in activeIndex until chain.steps.size) {
            val oldLayer = chain.steps[i]
            val filterToUse = if (i == activeIndex) filter else (oldLayer.config as? LayerConfig.Filter)?.filter
                ?: throw IllegalStateException("Step $i is not a filter layer")

            val resultImage = repository.applyFilter(currentImage, filterToUse)

            val updatedLayer = oldLayer.copy(
                name = filterToUse.name,
                image = resultImage,
                config = LayerConfig.Filter(filterToUse)
            )

            newSteps[i] = updatedLayer
            currentImage = resultImage
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

    suspend fun removeStep(workspace: ImageWorkspace, index: Int): Result<ImageWorkspace> = runCatching {
        val chain = workspace.pipeline ?: throw IllegalStateException("No active chain")

        // 1. 边界检查
        if (index !in chain.steps.indices) {
            throw IllegalArgumentException("Invalid step index: $index")
        }

        val oldSteps = chain.steps

        // 2. 确定“重算起点”的输入图
        // 如果删除的是 index=0，则输入是原始 Asset
        // 如果删除的是 index>0，则输入是 steps[index-1] 的结果
        val baseInputLayer = if (index == 0) {
            workspace.assets.find { it.id == chain.inputAssetId }
        } else {
            oldSteps[index - 1]
        } ?: throw IllegalStateException("Base input layer not found")

        var currentImage = baseInputLayer.image
            ?: throw IllegalStateException("Base image data missing")

        // 3. 构建新列表（先移除目标，再重算后续）
        val newSteps = oldSteps.toMutableList()
        newSteps.removeAt(index)

        // 从被删除位置的下标开始（因为后面的元素前移了，下标就是 i），重新应用滤镜
        // 注意：newSteps.size 已经变小了
        for (i in index until newSteps.size) {
            val layerToRecalculate = newSteps[i]
            val filterConfig = layerToRecalculate.config as? LayerConfig.Filter
                ?: continue // 如果不是滤镜层（理论上不应发生），跳过

            // 使用上一轮的 currentImage 作为输入，应用当前层的滤镜参数
            val resultImage = repository.applyFilter(currentImage, filterConfig.filter)

            // 更新层数据
            newSteps[i] = layerToRecalculate.copy(image = resultImage)

            // 传递给下一轮
            currentImage = resultImage
        }

        // 4. 修正 activeIndex
        // 如果删除的是当前选中的步骤，或者选中的步骤在被删除步骤之后，需要调整
        val currentActive = chain.activeIndex
        val newActiveIndex = when {
            // 如果列表空了
            newSteps.isEmpty() -> -1
            // 如果删除的是当前选中项，选中前一项（如果前一项没了就选第一项或 -1）
            currentActive == index -> (index - 1).coerceAtLeast(if (newSteps.isNotEmpty()) 0 else -1)
            // 如果选中的在被删除的后面，减 1
            currentActive > index -> currentActive - 1
            // 如果选中的在被删除的前面，保持不变
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
}