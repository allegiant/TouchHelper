package org.eu.freex.tools.modules.image.application

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
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
import kotlin.math.roundToInt

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
        var newChain = workspace.activeChain
        if (newChain?.inputAssetId == assetId) {
            val nextAsset = newAssets.firstOrNull()
            newChain = if (nextAsset != null) {
                ProcessingChain(inputAssetId = nextAsset.id, activeIndex = -1)
            } else {
                null
            }
        }
        return workspace.copy(
            assets = newAssets,
            activeChain = newChain,
            fontGenerator = if (workspace.activeChain?.inputAssetId == assetId) null else workspace.fontGenerator
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

    // --- 裁剪 (核心修复：逻辑坐标 -> 物理坐标映射) ---
    suspend fun cropImage(sourceLayer: ImageLayer, cropRect: Rectangle): Result<ImageLayer> = runCatching {
        val source = sourceLayer.image ?: throw IllegalStateException("No source image")

        var realRect = cropRect

        // 如果是屏幕截图，需要处理 DPI 坐标转换
        if (sourceLayer.config is LayerConfig.Origin && sourceLayer.config.sourcePath == "mem") {
            val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
            var logicalTotalWidth = 0.0

            // 计算逻辑屏幕总宽度
            for (screen in ge.screenDevices) {
                logicalTotalWidth += screen.defaultConfiguration.bounds.width
            }

            // 计算缩放比例：物理图片宽度 / 逻辑屏幕宽度
            // 如果 captureScreen 修复成功，source.width 应该是物理宽度 (如 3840)，而 logicalTotalWidth 是逻辑宽度 (如 1920)
            // scaleX 约为 2.0
            val scaleX = source.width.toDouble() / logicalTotalWidth

            // 安全检查：如果比例接近 1.0，说明可能是低分屏或截图未生效，不做处理
            // 如果比例明显大于 1 (如 > 1.05)，说明是高分屏，需要放大 CropRect
            if (scaleX > 1.05) {
                realRect = Rectangle(
                    (cropRect.x * scaleX).roundToInt(),
                    (cropRect.y * scaleX).roundToInt(), // 注意：这里通常也用 scaleX，除非是异形屏
                    (cropRect.width * scaleX).roundToInt(),
                    (cropRect.height * scaleX).roundToInt()
                )
            }
        }

        // 增加越界保护，防止 crash
        val safeX = realRect.x.coerceIn(0, source.width - 1)
        val safeY = realRect.y.coerceIn(0, source.height - 1)
        val safeW = realRect.width.coerceAtMost(source.width - safeX)
        val safeH = realRect.height.coerceAtMost(source.height - safeY)
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

    // --- 流程管理 (保持不变) ---
    fun activateAsset(workspace: ImageWorkspace, assetId: String): ImageWorkspace {
        if (workspace.activeChain?.inputAssetId == assetId) return workspace
        return workspace.copy(
            activeChain = ProcessingChain(inputAssetId = assetId, activeIndex = -1),
            fontGenerator = null
        )
    }

    suspend fun addFilterStep(workspace: ImageWorkspace, filter: ImageFilter): Result<ImageWorkspace> = runCatching {
        val chain = workspace.activeChain ?: throw IllegalStateException("No active image")
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
        workspace.copy(activeChain = chain.copy(steps = newSteps, activeIndex = newSteps.lastIndex))
    }

    suspend fun updateFilterStep(workspace: ImageWorkspace, filter: ImageFilter): Result<ImageWorkspace> = runCatching {
        val chain = workspace.activeChain ?: throw IllegalStateException("No active chain")
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
        workspace.copy(activeChain = chain.copy(steps = newSteps))
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
        val chain = workspace.activeChain ?: throw IllegalStateException("No chain")
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

    // --- 持久化 ---
    suspend fun saveWorkspace(file: File, workspace: ImageWorkspace): Result<Unit> = runCatching {
        val data = json.encodeToString(workspace)
        file.writeText(data)
    }

    suspend fun loadWorkspace(file: File): Result<ImageWorkspace> = runCatching {
        val workspace = json.decodeFromString<ImageWorkspace>(file.readText())
        val restoredAssets = workspace.assets.map { layer ->
            if (layer.config is LayerConfig.Origin && File(layer.config.sourcePath).exists()) {
                layer.copy(image = repository.loadFromFile(File(layer.config.sourcePath)))
            } else layer
        }
        workspace.copy(assets = restoredAssets)
    }
}