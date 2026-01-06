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

    // --- 截图 ---
    suspend fun captureScreen(): Result<ImageLayer> = runCatching {
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
                var allScreenBounds = Rectangle()

                for (screen in screens) {
                    val bounds = screen.defaultConfiguration.bounds
                    allScreenBounds = allScreenBounds.union(bounds)
                }

                val robot = Robot()
                // 这里 allScreenBounds 是逻辑坐标，但 createScreenCapture 在高分屏下会返回物理像素大图
                val screenCapture = robot.createScreenCapture(allScreenBounds)

                ImageLayer(
                    name = "Capture_${System.currentTimeMillis()}",
                    image = screenCapture,
                    // 标记这是内存中的截图
                    config = LayerConfig.Origin("mem")
                )
            }
        } finally {
            withContext(Dispatchers.Main) {
                visibleWindows.forEach {
                    it.isVisible = true
                    it.toFront()
                }
            }
        }
    }

    // --- 裁剪 (核心修复：DPI 缩放处理) ---
    suspend fun cropImage(sourceLayer: ImageLayer, cropRect: Rectangle): Result<ImageLayer> = runCatching {
        val source = sourceLayer.image ?: throw IllegalStateException("No source image")

        // 1. 默认使用传入的 rect (适用于普通图片编辑)
        var realRect = cropRect

        // 2. 如果是屏幕截图，需要进行 DPI 坐标转换
        // 判断依据：config 是 Origin("mem")，且图片尺寸可能大于逻辑屏幕尺寸
        if (sourceLayer.config is LayerConfig.Origin && sourceLayer.config.sourcePath == "mem") {
            val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
            var logicalBounds = Rectangle()
            for (screen in ge.screenDevices) {
                logicalBounds = logicalBounds.union(screen.defaultConfiguration.bounds)
            }

            // 计算缩放比例 (物理宽 / 逻辑宽)
            // 例如：屏幕逻辑宽 1920，截图出来是 3840 (200% 缩放)
            // scale = 2.0
            val scaleX = source.width.toDouble() / logicalBounds.width.toDouble()
            val scaleY = source.height.toDouble() / logicalBounds.height.toDouble()

            // 如果比例偏差较大(说明有缩放)，则修正裁剪区域
            if (scaleX > 1.05 || scaleY > 1.05) {
                realRect = Rectangle(
                    (cropRect.x * scaleX).roundToInt(),
                    (cropRect.y * scaleY).roundToInt(),
                    (cropRect.width * scaleX).roundToInt(),
                    (cropRect.height * scaleY).roundToInt()
                )
            }
        }

        val cropped = repository.crop(source, realRect)
        ImageLayer(
            name = "Crop_${sourceLayer.name}",
            image = cropped,
            config = LayerConfig.Origin("cropped")
        )
    }

    // --- 流程管理 ---

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