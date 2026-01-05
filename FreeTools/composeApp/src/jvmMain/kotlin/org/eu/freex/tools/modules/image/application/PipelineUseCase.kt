package org.eu.freex.tools.modules.image.application

import org.eu.freex.tools.modules.image.domain.model.ImageFilter
import org.eu.freex.tools.modules.image.domain.model.Pipeline
import org.eu.freex.tools.modules.image.domain.model.Project
import org.eu.freex.tools.modules.image.domain.model.WorkImage
import org.eu.freex.tools.modules.image.domain.service.FilterService

/**
 * 流水线业务用例：处理计算、重算、预览
 */
class PipelineUseCase(
    private val filterService: FilterService
) {

    suspend fun processSingle(
        source: WorkImage,
        filter: ImageFilter
    ): Result<WorkImage> {
        return filterService.processSingle(source, filter)
    }

    /**
     * 全量刷新流水线 (用于切图时)
     */
    suspend fun refreshPipeline(
        source: WorkImage,
        pipeline: Pipeline
    ): Result<Pipeline> {
        val filters = pipeline.steps.mapNotNull { it.appliedFilter }
        return filterService.processChain(source, filters).map { newSteps ->
            pipeline.copy(
                steps = newSteps,
                activeIndex = newSteps.size
                // 注意：这里不重置 draft，由调用方决定
            )
        }
    }

    /**
     * 更新当前步骤，并自动重算后续
     */
    suspend fun updateCurrentStep(
        pipeline: Pipeline,
        newStepImage: WorkImage
    ): Result<Pipeline> = runCatching {
        val currentIndex = pipeline.activeIndex
        if (currentIndex <= 0) throw IllegalArgumentException("Cannot update source image directly")

        // 1. 获取后续需要重放的滤镜
        val filtersToReplay = pipeline.getFiltersAfter(currentIndex)

        // 2. 重算后续
        val recalculatedTail = if (filtersToReplay.isNotEmpty()) {
            filterService.processChain(newStepImage, filtersToReplay).getOrThrow()
        } else {
            emptyList()
        }

        // 3. 替换并返回新 Model
        pipeline.replaceSteps(currentIndex - 1, listOf(newStepImage) + recalculatedTail)
    }

    /**
     * 删除指定步骤
     */
    suspend fun deleteStep(
        pipeline: Pipeline,
        project: Project,
        indexToDelete: Int
    ): Result<Pipeline> = runCatching {
        if (indexToDelete <= 0) throw IllegalArgumentException("Cannot delete source image")

        // 1. 找到输入源
        val prevImage = pipeline.getInputImage(indexToDelete, project.activeImage)
            ?: throw IllegalStateException("Previous image not found")

        // 2. 获取后续滤镜
        val filtersToReplay = pipeline.getFiltersAfter(indexToDelete)

        // 3. 重算
        val newSuffix = if (filtersToReplay.isNotEmpty()) {
            filterService.processChain(prevImage, filtersToReplay).getOrThrow()
        } else {
            emptyList()
        }

        // 4. 替换
        pipeline.replaceSteps(indexToDelete - 1, newSuffix)
    }
}