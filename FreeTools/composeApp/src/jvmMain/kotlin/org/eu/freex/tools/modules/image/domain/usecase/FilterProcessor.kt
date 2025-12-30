package org.eu.freex.tools.modules.image.domain.usecase

import org.eu.freex.tools.modules.image.domain.model.AppFilter
import org.eu.freex.tools.modules.image.domain.model.WorkImage
import org.eu.freex.tools.modules.image.domain.repository.ImageRepository

/**
 * 滤镜逻辑处理器 (Pure Use Case)
 * 职责：
 * 1. 封装具体的滤镜计算逻辑
 * 2. 处理流水线的级联重算
 * 3. 不持有任何 UI 状态，只返回 Result
 */
class FilterProcessor(
    private val repository: ImageRepository
) {

    /**
     * 单步应用：对一张图应用一个滤镜
     */
    suspend fun applyFilter(
        source: WorkImage,
        filter: AppFilter
    ): Result<WorkImage> = runCatching {
        // Repository 内部已切换到 Dispatchers.Default，此处直接调用即可
        repository.applyFilter(source, filter)
    }

    /**
     * 链式重算：给定一个起始图，依次应用一系列滤镜
     * (用于“删除步骤”后，重新生成后续的所有步骤)
     */
    suspend fun processChain(
        initialSource: WorkImage,
        filters: List<AppFilter>
    ): Result<List<WorkImage>> = runCatching {
        val results = ArrayList<WorkImage>(filters.size)
        var currentSource = initialSource

        for (filter in filters) {
            val result = repository.applyFilter(currentSource, filter)
            results.add(result)
            currentSource = result
        }
        results
    }
}