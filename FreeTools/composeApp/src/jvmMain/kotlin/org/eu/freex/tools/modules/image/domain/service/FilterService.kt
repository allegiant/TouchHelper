package org.eu.freex.tools.modules.image.domain.service

import org.eu.freex.tools.modules.image.domain.model.ImageFilter
import org.eu.freex.tools.modules.image.domain.model.WorkImage
import org.eu.freex.tools.modules.image.domain.repository.ImageRepository

/**
 * 滤镜逻辑处理器 (Pure Use Case)
 * 职责：
 * 1. 封装具体的滤镜计算逻辑
 * 2. 处理流水线的级联重算
 * 3. 不持有任何 UI 状态，只返回 Result
 */
class FilterService(
    private val repository: ImageRepository
) {

    /**
     * 【核心方法】单步处理：对一张图应用一个滤镜
     * 用于：
     * 1. 滤镜预览 (Preview)：UI 拖动滑块时，基于上一步缓存快速生成预览图
     * 2. 增量计算：流水线添加新步骤时，只算这一步
     */
    suspend fun processSingle(
        source: WorkImage,
        filter: ImageFilter
    ): Result<WorkImage> = runCatching {
        // Repository 内部通常已切换到 Dispatchers.Default，此处直接调用即可
        // 这一步会真正调用 OpenCV 或算法库生成新的 BufferedImage
        repository.applyFilter(source, filter)
    }

    /**
     * 链式重算：给定一个起始图，依次应用一系列滤镜
     * 用于：
     * 1. 删除中间步骤后，级联重算后续所有步骤
     * 2. 批量处理
     */
    suspend fun processChain(
        initialSource: WorkImage,
        filters: List<ImageFilter>
    ): Result<List<WorkImage>> = runCatching {
        val results = ArrayList<WorkImage>(filters.size)
        var currentSource = initialSource

        for (filter in filters) {
            // 复用 applyFilter 逻辑（这里调用 repository 底层方法）
            val result = repository.applyFilter(currentSource, filter)
            results.add(result)
            // 将当前结果作为下一步的输入
            currentSource = result
        }
        results
    }
}