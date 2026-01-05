package org.eu.freex.tools.modules.image.presentation.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.eu.freex.tools.modules.image.application.PipelineUseCase
import org.eu.freex.tools.modules.image.application.ProjectUseCase
import org.eu.freex.tools.modules.image.domain.model.Pipeline
import org.eu.freex.tools.modules.image.domain.model.Project
import java.awt.image.BufferedImage

/**
 * ViewModel 能力契约
 * 纯净版：只暴露 UseCases 和 状态更新能力
 */
interface ImageActionScope {
    val state: ImageUiState
    val scope: CoroutineScope

    // --- UseCases (替代 Services) ---
    val pipelineUseCase: PipelineUseCase
    val projectUseCase: ProjectUseCase

    var filterPreviewJob: Job?

    // --- 基础设施 ---
    fun openLoading() { setState { copy(isLoading = true) } }
    fun closeLoading() { setState { copy(isLoading = false) } }
    fun showToast(message: String)
    fun setScreenCropper(image: BufferedImage?) { setState { copy(cropperImage = image) } }

    // --- 状态更新 ---
    fun setState(reducer: ImageUiState.() -> ImageUiState)
    fun launch(block: suspend ImageActionScope.() -> Unit)
    fun handleEvent(event: ImageUiEvent)

    // --- 语法糖 ---
    fun setProject(reducer: Project.() -> Project) {
        setState { copy(project = project.reducer()) }
    }
    fun setPipeline(reducer: Pipeline.() -> Pipeline) {
        setState { copy(pipeline = pipeline.reducer()) }
    }
}