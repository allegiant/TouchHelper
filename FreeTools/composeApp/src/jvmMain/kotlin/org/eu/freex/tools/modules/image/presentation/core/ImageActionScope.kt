package org.eu.freex.tools.modules.image.presentation.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.eu.freex.tools.modules.image.domain.model.Pipeline
import org.eu.freex.tools.modules.image.domain.model.Project
import org.eu.freex.tools.modules.image.domain.model.WorkImage
import org.eu.freex.tools.modules.image.domain.service.FilterService
import org.eu.freex.tools.modules.image.domain.service.ProjectService
import org.eu.freex.tools.modules.image.domain.service.ResourceService
import org.eu.freex.tools.modules.image.domain.service.SegmentationService
import java.awt.image.BufferedImage

/**
 * ViewModel 能力契约
 * Event通过此接口操作 ViewModel 的资源、状态和协程
 */
interface ImageActionScope {
    val state: ImageUiState // 1. 状态访问
    val scope: CoroutineScope // 用于启动协程

    // 处理器
    val filterService: FilterService
    val resourceService: ResourceService
    val segmentationService: SegmentationService
    val projectService: ProjectService
    var filterPreviewJob: Job? // 4. 专用状态 (如防抖 Job)


    fun openLoading() {
        setState { copy(isLoading = true) }
    }

    fun closeLoading() {
        setState { copy(isLoading = false)}
    }
    fun setScreenCropper(bufferedImage: BufferedImage?) {
        setState { copy(cropperImage = bufferedImage) }
    }

    // 【优化】新增一个名字更短的方法，且使用带接收者的 Lambda
    fun setState(reducer: ImageUiState.() -> ImageUiState)

    // --- 【核心优化 2】子状态更新语法糖 ---
    fun setProject(reducer: Project.() -> Project) {
        setState { copy(project = project.reducer()) }
    }

    fun setPipeline(reducer: Pipeline.() -> Pipeline) {
        setState { copy(pipeline = pipeline.reducer()) }
    }

    // --- 【核心优化 3】全自动 Launch (自动 Loading + 异常捕获) ---
    fun launch(block: suspend ImageActionScope.() -> Unit)

    fun showToast(message: String)

    // --- 【关键修复】暴露 handleEvent，允许事件内部触发其他事件 ---
    fun handleEvent(event: ImageUiEvent)
}

/**
 * 获取流水线中“上一步”的图片。
 * 用于滤镜修改或删除时，确定输入源是谁。
 */
fun ImageActionScope.getPrevStepImage(stepIndex: Int): WorkImage? {
    // 1. 如果是第 0 步 (第一个滤镜)，它的“上一步”就是原图
    if (stepIndex == 0) {
        return state.project.activeImage
    }
    // 2. 如果是第 N 步，它的“上一步”就是流水线里的第 N-1 步的结果
    else {
        return state.pipeline.steps.getOrNull(stepIndex - 1)
    }
}