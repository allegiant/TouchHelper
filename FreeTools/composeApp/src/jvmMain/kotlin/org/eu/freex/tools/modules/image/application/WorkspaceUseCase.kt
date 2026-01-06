package org.eu.freex.tools.modules.image.application

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.eu.freex.tools.modules.image.domain.model.*
import org.eu.freex.tools.modules.image.domain.model.font.FontGenerator
import org.eu.freex.tools.modules.image.domain.model.font.FontRect
import org.eu.freex.tools.modules.image.domain.model.font.Glyph
import org.eu.freex.tools.modules.image.domain.repository.LayerRepository
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Window
import java.io.File
import java.util.UUID

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
            fontGenerator = if (workspace.pipeline?.inputAssetId == assetId) null else workspace.fontGenerator
        )
    }

    // --- 截图 (终极修复：使用 MultiResolution API) ---
    suspend fun captureScreen(): Result<ImageLayer> = runCatching {
        // 1. 隐藏窗口 (保持不变)
        val visibleWindows = withContext(Dispatchers.Main) {
            val windows = Window.getWindows().filter { it.isVisible }
            windows.forEach { it.isVisible = false }
            windows
        }

        try {
            delay(300)

            withContext(Dispatchers.IO) {
                val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
                val screens = ge.screenDevices
                var logicalBounds = Rectangle()

                // 2. 计算所有屏幕的【逻辑】边界总和
                // 注意：这里不要手动乘缩放比例了，直接用 bounds
                for (screen in screens) {
                    logicalBounds = logicalBounds.union(screen.defaultConfiguration.bounds)
                }

                val robot = Robot()

                // 3. 核心修改：使用 createMultiResolutionScreenCapture
                // 这个 API 会自动处理高分屏，返回多个分辨率的截图版本
                val finalImage = try {
                    val mri = robot.createMultiResolutionScreenCapture(logicalBounds)
                    // 从变体中找到分辨率最高的那张图 (即物理像素图)
                    val variants = mri.resolutionVariants
                    val bestVariant = variants.maxByOrNull { it.getWidth(null) }

                    // 确保是 BufferedImage
                    if (bestVariant is java.awt.image.BufferedImage) {
                        bestVariant
                    } else {
                        // 如果类型不对（极少见），回退到普通截图
                        robot.createScreenCapture(logicalBounds)
                    }
                } catch (e: NoSuchMethodError) {
                    // 兼容旧版 JDK (虽然 Compose Desktop 通常自带 JDK 11+)
                    robot.createScreenCapture(logicalBounds)
                }

                ImageLayer(
                    name = "Capture_${System.currentTimeMillis()}",
                    image = finalImage,
                    config = LayerConfig.Origin("mem")
                )
            }
        } finally {
            // 4. 恢复窗口 (保持不变)
            withContext(Dispatchers.Main) {
                visibleWindows.forEach {
                    it.isVisible = true
                    it.toFront()
                }
            }
        }
    }

    // --- 裁剪 (逻辑简化：直接信任传入的物理坐标) ---
    suspend fun cropImage(sourceLayer: ImageLayer, cropRect: Rectangle): Result<ImageLayer> = runCatching {
        val source = sourceLayer.image ?: throw IllegalStateException("No source image")

        // 移除之前的 GraphicsEnvironment/Scale 计算逻辑
        // 因为 ScreenCropperDialog 现在已经根据 View/Bitmap 比例计算好了真实的物理坐标

        // 唯一的保护是防止越界 (例如 1px 的误差)
        val safeX = cropRect.x.coerceIn(0, source.width - 1)
        val safeY = cropRect.y.coerceIn(0, source.height - 1)
        val safeW = cropRect.width.coerceAtMost(source.width - safeX)
        val safeH = cropRect.height.coerceAtMost(source.height - safeY)
        val safeRect = Rectangle(safeX, safeY, safeW, safeH)

        if (safeRect.width <= 0 || safeRect.height <= 0) {
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

    // --- 字库 ---
    suspend fun startFontGeneration(workspace: ImageWorkspace): Result<ImageWorkspace> = runCatching {
        val chain = workspace.pipeline ?: throw IllegalStateException("No chain")
        val finalLayer = chain.getFinalLayer(workspace.assets) ?: throw IllegalStateException("No layer")
        val finalImage = finalLayer.image ?: throw IllegalStateException("No data")

        val seg = GridSegmentation(3, 3)
        val rects = repository.segment(finalImage, seg)
        val glyphs = rects.map { r ->
            val sub = repository.crop(finalImage, r)
            Glyph(
                id = UUID.randomUUID().toString(),
                char = null,
                layer = ImageLayer(
                    name = "Glyph",
                    image = sub,
                    config = LayerConfig.Origin("sub")
                ),
                bounds = FontRect(r.x, r.y, r.width, r.height)
            )
        }
        workspace.copy(fontGenerator = FontGenerator(finalLayer, seg, glyphs))
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

        workspace.copy(
            assets = restoredAssets,
            pipeline = restoredActiveChain
        )
    }

    /**
     * 辅助函数：根据滤镜配置重新计算流水线中每一步的图片
     */
    private suspend fun restoreChainImages(assets: List<ImageLayer>, chain: Pipeline): Pipeline {
        val baseLayer = assets.find { it.id == chain.inputAssetId } ?: return chain
        var currentImage = baseLayer.image ?: return chain

        val restoredSteps = chain.steps.map { step ->
            if (step.config is LayerConfig.Filter) {
                val filter = step.config.filter
                // 重新执行滤镜计算
                val resultImage = repository.applyFilter(currentImage, filter)
                currentImage = resultImage
                step.copy(image = resultImage)
            } else {
                step
            }
        }

        return chain.copy(steps = restoredSteps)
    }
}