package org.eu.freex.tools.modules.image.application


import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.eu.freex.tools.common.utils.ImageUtils
import org.eu.freex.tools.modules.image.domain.model.ImageLayer
import org.eu.freex.tools.modules.image.domain.model.ImageWorkspace
import org.eu.freex.tools.modules.image.domain.model.LayerConfig
import org.eu.freex.tools.modules.image.domain.model.Pipeline
import org.eu.freex.tools.modules.image.domain.repository.LayerRepository
import org.eu.freex.tools.modules.image.domain.repository.ProjectRepository
import java.io.File

class ProjectAssetUseCase(
    private val projectRepo: ProjectRepository,
    private val layerRepo: LayerRepository
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    // --- 导入图片 ---
    suspend fun importImage(file: File): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val image = layerRepo.loadFromFile(file)
            val newLayer = ImageLayer(
                image = image,
                name = file.name,
                config = LayerConfig.Origin(file.absolutePath),
            )
            // 增量更新：追加图片
            projectRepo.updateWorkspace { old ->
                old.copy(assets = old.assets + newLayer)
            }
        }
    }

    /**
     *  --- 删除图片 (包含级联逻辑) ---
     */
    suspend fun removeImage(assetId: String): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            projectRepo.updateWorkspace { workspace ->
                // A. 从列表中移除
                val newAssets = workspace.assets.filter { it.id != assetId }
                // B. 检查是否影响当前的 Pipeline
                var newChain = workspace.pipeline
                if (newChain?.inputAssetId == assetId) {
                    // 如果删的是当前选中的图，尝试选中下一张
                    val nextAsset = newAssets.firstOrNull()
                    newChain = if (nextAsset != null) {
                        // 重置 Pipeline，以新图为起点
                        Pipeline(inputAssetId = nextAsset.id, activeIndex = -1)
                    } else {
                        null // 没图了，清空 Pipeline
                    }
                }
                workspace.copy(assets = newAssets, pipeline = newChain)
            }
        }
    }

    // --- 截图 ---
    suspend fun captureScreen(): Result<Unit> = runCatching {
        withContext(Dispatchers.Default) {
            val image = layerRepo.captureScreen()
            val newLayer = ImageLayer(
                name = "Capture_${System.currentTimeMillis()}",
                image = image,
                config = LayerConfig.Origin("mem") // 标记为内存来源
            )
            // 自动添加到列表
            projectRepo.updateWorkspace { old ->
                old.copy(assets = old.assets + newLayer)
            }
        }
    }

    // --- 5. [新增] 导出单张图片 ---
    suspend fun exportImage(layer: ImageLayer, file: File): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val image = layer.image ?: throw IllegalStateException("Empty image")
            layerRepo.saveToFile(image, file)
        }
    }

    // --- 加载项目 (核心：JSON + 图片恢复) ---
    suspend fun loadProject(file: File): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val jsonString = file.readText()
            val rawWorkspace = json.decodeFromString<ImageWorkspace>(jsonString)

            // 1. 恢复所有资源图
            val restoredAssets = rawWorkspace.assets.map { asset ->
                val config = asset.config
                if (config is LayerConfig.Origin && config.sourcePath != "mem") {
                    val imgFile = File(config.sourcePath)
                    if (imgFile.exists()) {
                        asset.copy(image = layerRepo.loadFromFile(imgFile))
                    } else asset
                } else asset
            }

            // 2. 恢复唯一的流水线图片数据
            var restoredPipeline = rawWorkspace.pipeline
            if (restoredPipeline != null) {
                val inputAsset = restoredAssets.find { it.id == restoredPipeline.inputAssetId }
                var currentImage = inputAsset?.image
                if (currentImage != null) {
                    val oldSteps = restoredPipeline.steps
                    // 提取所有滤镜配置
                    val filters = oldSteps.mapNotNull { (it.config as? LayerConfig.Filter)?.filter }
                    // 批量调用底层算法
                    val resultImages = layerRepo.applyPipeline(currentImage, filters)
                    // 回填结果图片到 Steps 中
                    var resultIndex = 0
                    val newSteps = oldSteps.map { step ->
                        if (step.config is LayerConfig.Filter) {
                            val resultImage = resultImages.getOrNull(resultIndex++)
                            step.copy(image = resultImage)
                        } else {
                            step
                        }
                    }
                    restoredPipeline = restoredPipeline.copy(steps = newSteps)
                }
            }
            // 4. 恢复 FontLibrary 预览图 (从 01 二进制串重建 Bitmap)
            val restoredLib = rawWorkspace.fontLibrary.map { item ->
                if (item.displayBitmap == null && item.binaryData.isNotEmpty()) {
                    val bitmap = ImageUtils.binaryStringToBitmap(item.width, item.height, item.binaryData)
                    item.copy(displayBitmap = bitmap)
                } else {
                    item
                }
            }

            // 5. 组装最终对象并重置仓库
            val finalWorkspace = rawWorkspace.copy(
                assets = restoredAssets,
                pipeline = restoredPipeline,
                fontLibrary = restoredLib
            )

            projectRepo.resetWorkspace(finalWorkspace)
        }
    }

    // --- 保存项目 ---
    suspend fun saveProject(file: File): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val current = projectRepo.workspace.value
            val jsonString = json.encodeToString(current)
            file.writeText(jsonString)
        }
    }
}

