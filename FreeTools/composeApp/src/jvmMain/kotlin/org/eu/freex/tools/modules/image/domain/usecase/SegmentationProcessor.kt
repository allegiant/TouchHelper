package org.eu.freex.tools.modules.image.domain.usecase

import org.eu.freex.tools.modules.image.domain.model.AppSegmentation
import org.eu.freex.tools.modules.image.domain.model.WorkImage
import org.eu.freex.tools.modules.image.domain.repository.ImageRepository
import androidx.compose.ui.geometry.Rect

/**
 * 切割逻辑处理器 (Pure Use Case)
 * 职责：
 * 1. 接收图片和策略对象
 * 2. 调用 Repository 执行切割
 * 3. 返回切割区域(Rect)和子图(WorkImage)的组合结果
 */
class SegmentationProcessor(
    private val repository: ImageRepository
) {
    // 定义一个结果数据类，比 Pair 更清晰 (可选，也可以继续用 Pair)
    data class ResultData(
        val rects: List<Rect>,
        val subImages: List<WorkImage>
    )

    /**
     * 执行切割
     */
    suspend fun segment(
        source: WorkImage,
        strategy: AppSegmentation
    ): Result<ResultData> = runCatching {
        // 调用 Repository (接口已改为接收 AppSegmentation)
        val (rects, subImages) = repository.segmentImage(source, strategy)
        ResultData(rects, subImages)
    }
}