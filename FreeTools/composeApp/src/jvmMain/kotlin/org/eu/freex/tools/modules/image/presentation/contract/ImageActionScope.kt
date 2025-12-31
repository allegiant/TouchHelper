package org.eu.freex.tools.modules.image.presentation.contract

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.eu.freex.tools.modules.image.domain.model.WorkImage
import org.eu.freex.tools.modules.image.domain.usecase.FilterProcessor
import org.eu.freex.tools.modules.image.domain.usecase.ProjectProcessor
import org.eu.freex.tools.modules.image.domain.usecase.ResourceProcessor
import org.eu.freex.tools.modules.image.domain.usecase.SegmentationProcessor

/**
 * ViewModel 能力契约
 * Event通过此接口操作 ViewModel 的资源、状态和协程
 */
interface ImageActionScope {
    val state: ImageUiState // 1. 状态访问
    val scope: CoroutineScope // 用于启动协程

    // 处理器
    val filterProcessor: FilterProcessor
    val resourceProcessor: ResourceProcessor
    val segmentationProcessor: SegmentationProcessor
    val projectProcessor: ProjectProcessor
    var filterPreviewJob: Job? // 4. 专用状态 (如防抖 Job)

    // 【优化】新增一个名字更短的方法，且使用带接收者的 Lambda
    fun setState(reducer: ImageUiState.() -> ImageUiState)

    // --- 【核心优化 2】子状态更新语法糖 ---
    fun setProject(reducer: ProjectState.() -> ProjectState) {
        setState { copy(project = project.reducer()) }
    }

    fun setUi(reducer: UiInteractionState.() -> UiInteractionState) {
        setState { copy(ui = ui.reducer()) }
    }

    fun setSegmentation(reducer: SegmentationState.() -> SegmentationState) {
        setState { copy(segmentation = segmentation.reducer()) }
    }

    // --- 【核心优化 3】全自动 Launch (自动 Loading + 异常捕获) ---
    fun launch(block: suspend ImageActionScope.() -> Unit)

    fun showToast(message: String)
}

/**
 * 获取流水线中“上一步”的图片。
 * 用于滤镜修改或删除时，确定输入源是谁。
 */
fun ImageActionScope.getPrevStepImage(stepIndex: Int): WorkImage? {
    // 适配新的 State 结构 (通过 state.project 访问)
    // 注意：如果你在第一步保留了 state.currentSourceImage 的兼容属性，这里也可以直接用旧写法

    // 新写法 (更严谨，直接访问 ProjectState):
    val project = state.project
    return if (stepIndex == 0) {
        project.sourceImages.getOrNull(project.selectedSourceIndex)
    } else {
        project.pipelineSteps.getOrNull(stepIndex - 1)
    }
}