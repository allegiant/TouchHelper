package org.eu.freex.tools.modules.image.application

import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eu.freex.tools.modules.image.domain.model.ImageFilter
import org.eu.freex.tools.modules.image.domain.model.ImageLayer
import org.eu.freex.tools.modules.image.domain.model.LayerConfig
import org.eu.freex.tools.modules.image.domain.model.Pipeline
import org.eu.freex.tools.modules.image.domain.repository.LayerRepository
import org.eu.freex.tools.modules.image.domain.repository.ProjectRepository

class ImageProcessingUseCase(
    private val projectRepo: ProjectRepository,
    private val layerRepo: LayerRepository
) {
    // --- 1. 裁剪 (延伸裁剪功能) ---
    // 这是一个破坏性操作，会生成一个新的 Origin Asset 并重置 Pipeline
    suspend fun cropImage(sourceLayer: ImageLayer, cropRect: Rect): Result<Unit> = runCatching {
        withContext(Dispatchers.Default) {
            val source = sourceLayer.image ?: throw IllegalStateException("No source image")

            // 安全检查
            val imageBounds = Rect(0f, 0f, source.width.toFloat(), source.height.toFloat())
            val safeRect = cropRect.intersect(imageBounds)
            if (safeRect.isEmpty) throw IllegalStateException("Invalid crop area")

            val croppedImage = layerRepo.crop(source, safeRect)

            val newLayer = ImageLayer(
                name = "Crop_${sourceLayer.name}",
                image = croppedImage,
                config = LayerConfig.Origin("cropped")
            )

            // 3. 添加到资源列表 (原子更新)
            projectRepo.updateWorkspace { old ->
                old.copy(assets = old.assets + newLayer)
            }
            // 4. [关键修复] 激活新资源 (自动继承当前滤镜链并重算)
            activateAsset(newLayer.id)
        }
    }

    // --- 2. 切换当前编辑的图片 (Activate Asset) ---
    // 切换底图，但保留当前的滤镜链配置，并重新计算结果
    suspend fun activateAsset(assetId: String) {
        withContext(Dispatchers.Default) {
            val workspace = projectRepo.workspace.value
            if (workspace.pipeline?.inputAssetId == assetId) return@withContext

            val newBaseLayer = workspace.assets.find { it.id == assetId } ?: return@withContext
            val baseImage = newBaseLayer.image ?: return@withContext

            // 获取旧的滤镜链配置
            val oldSteps = workspace.pipeline?.steps ?: emptyList()
            val filtersToApply = oldSteps.mapNotNull { (it.config as? LayerConfig.Filter)?.filter }

            // 批量重跑滤镜
            val resultImages = if (filtersToApply.isNotEmpty()) {
                layerRepo.applyPipeline(baseImage, filtersToApply)
            } else {
                emptyList()
            }

            // 重组 Steps
            var resultIndex = 0
            val newSteps = oldSteps.map { step ->
                if (step.config is LayerConfig.Filter) {
                    val newImage = resultImages.getOrNull(resultIndex++)
                    step.copy(image = newImage) // config 保持不变
                } else {
                    step
                }
            }

            val newChain = Pipeline(
                inputAssetId = assetId,
                steps = newSteps,
                activeIndex = if (newSteps.isNotEmpty()) newSteps.lastIndex else -1
            )

            projectRepo.updateWorkspace { it.copy(pipeline = newChain) }
        }
    }

    // --- 3. 添加滤镜 ---
    suspend fun addFilterStep(filter: ImageFilter) {
        withContext(Dispatchers.Default) {
            val workspace = projectRepo.workspace.value
            val chain = workspace.pipeline ?: return@withContext

            // 获取当前步骤的输出作为下一步的输入
            val inputLayer = chain.getActiveLayer(workspace.assets) ?: return@withContext
            val inputImage = inputLayer.image ?: return@withContext

            // 计算新滤镜
            val result = layerRepo.applyFilter(inputImage, filter)

            val newLayer = ImageLayer(
                name = filter.name,
                image = result,
                config = LayerConfig.Filter(filter)
            )

            // 截断：如果在中间步骤添加，后面的步骤会被丢弃
            val currentSteps = if (chain.activeIndex == -1) emptyList() else chain.steps.take(chain.activeIndex + 1)
            val newSteps = currentSteps + newLayer

            val newChain = chain.copy(steps = newSteps, activeIndex = newSteps.lastIndex)
            projectRepo.updateWorkspace { it.copy(pipeline = newChain) }
        }
    }

    // --- 4. 更新滤镜参数 ---
    suspend fun updateFilterStep(filter: ImageFilter) {
        withContext(Dispatchers.Default) {
            val workspace = projectRepo.workspace.value
            val chain = workspace.pipeline ?: return@withContext
            val activeIndex = chain.activeIndex
            if (activeIndex == -1) return@withContext

            // 1. 确定重算起点 (上一层的输出)
            val baseInputLayer = if (activeIndex == 0) {
                workspace.assets.find { it.id == chain.inputAssetId }
            } else {
                chain.steps.getOrNull(activeIndex - 1)
            } ?: return@withContext
            val baseImage = baseInputLayer.image ?: return@withContext

            // 2. 收集需要重算的滤镜 (当前这层用新参数，后面层用旧参数)
            val filtersToApply = mutableListOf<ImageFilter>()
            filtersToApply.add(filter)
            for (i in (activeIndex + 1) until chain.steps.size) {
                (chain.steps[i].config as? LayerConfig.Filter)?.filter?.let { filtersToApply.add(it) }
            }

            // 3. 批量计算
            val resultImages = layerRepo.applyPipeline(baseImage, filtersToApply)

            // 4. 回填结果
            val newSteps = chain.steps.toMutableList()
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

            projectRepo.updateWorkspace {
                it.copy(pipeline = chain.copy(steps = newSteps))
            }
        }
    }

    // --- 5. 删除滤镜 ---
    suspend fun removeStep(index: Int) {
        withContext(Dispatchers.Default) {
            val workspace = projectRepo.workspace.value
            val chain = workspace.pipeline ?: return@withContext
            if (index !in chain.steps.indices) return@withContext

            // 1. 确定重算起点 (index-1)
            val baseInputLayer = if (index == 0) {
                workspace.assets.find { it.id == chain.inputAssetId }
            } else {
                chain.steps.getOrNull(index - 1)
            } ?: return@withContext
            val baseImage = baseInputLayer.image ?: return@withContext

            // 2. 收集 index+1 之后的所有滤镜
            val filtersToApply = mutableListOf<ImageFilter>()
            for (i in (index + 1) until chain.steps.size) {
                (chain.steps[i].config as? LayerConfig.Filter)?.filter?.let { filtersToApply.add(it) }
            }

            // 3. 批量计算
            val resultImages = layerRepo.applyPipeline(baseImage, filtersToApply)

            // 4. 构建新列表 (移除 index)
            val newSteps = chain.steps.toMutableList()
            newSteps.removeAt(index)

            // 回填后续结果
            for ((j, resultImage) in resultImages.withIndex()) {
                val stepIndex = index + j // 此时 newSteps 已经移除了一个元素，所以对齐是对的
                val oldLayer = newSteps[stepIndex]
                newSteps[stepIndex] = oldLayer.copy(image = resultImage)
            }

            // 修正 activeIndex
            val currentActive = chain.activeIndex
            val newActiveIndex = when {
                newSteps.isEmpty() -> -1
                currentActive == index -> (index - 1).coerceAtLeast(if (newSteps.isNotEmpty()) 0 else -1)
                currentActive > index -> currentActive - 1
                else -> currentActive
            }

            projectRepo.updateWorkspace {
                it.copy(pipeline = chain.copy(steps = newSteps, activeIndex = newActiveIndex))
            }
        }
    }

    // --- 6. 计算预览 (瞬态，不更新 State) ---
    suspend fun calculatePreview(inputLayer: ImageLayer, filter: ImageFilter): ImageLayer? {
        val inputImage = inputLayer.image ?: return null
        val result = layerRepo.applyFilter(inputImage, filter)
        return ImageLayer(
            name = "Preview",
            image = result,
            config = LayerConfig.Filter(filter)
        )
    }
}