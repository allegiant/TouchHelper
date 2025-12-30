package org.eu.freex.tools.modules.image.presentation.contract

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.eu.freex.tools.modules.image.domain.model.WorkImage
import org.eu.freex.tools.modules.image.domain.usecase.*

/**
 * ViewModel 能力契约
 * Event通过此接口操作 ViewModel 的资源、状态和协程
 */
interface ImageActionScope {
    // 1. 状态访问
    val state: ImageUiState
    fun updateState(reducer: (ImageUiState) -> ImageUiState)

    // 2. 交互与副作用
    fun showToast(message: String)
    val scope: CoroutineScope // 用于启动协程

    // 3. 业务处理器 (Use Cases)
    val filterProcessor: FilterProcessor
    val resourceProcessor: ResourceProcessor
    val segmentationProcessor: SegmentationProcessor
    val projectProcessor: ProjectProcessor

    // 4. 专用状态 (如防抖 Job)
    var filterPreviewJob: Job?

    // 5. 辅助方法：快速启动协程并处理 Loading/Error
    fun launch(block: suspend ImageActionScope.() -> Unit)
}

// 扩展方法：获取前一步的图片 (供 Modify/Delete 使用)
fun ImageActionScope.getPrevStepImage(stepIndex: Int): WorkImage? {
    return if (stepIndex == 0) state.currentSourceImage else state.pipelineSteps.getOrNull(stepIndex - 1)
}