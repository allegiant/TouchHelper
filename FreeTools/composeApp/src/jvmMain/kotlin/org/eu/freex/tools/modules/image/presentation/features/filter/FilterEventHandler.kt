package org.eu.freex.tools.modules.image.presentation.features.filter

import org.eu.freex.tools.modules.image.application.PipelineUseCase
import org.eu.freex.tools.modules.image.domain.model.ViewFilter
import org.eu.freex.tools.modules.image.presentation.core.FilterEvent
import org.eu.freex.tools.modules.image.presentation.core.ImageEventHandler
import org.eu.freex.tools.modules.image.presentation.core.ImageUiEvent
import org.eu.freex.tools.modules.image.presentation.core.ImageUiState

class FilterEventHandler(
    private val pipelineUseCase: PipelineUseCase
) : ImageEventHandler {

    override suspend fun handle(
        event: ImageUiEvent,
        state: ImageUiState,
        showToast: (String) -> Unit
    ): ImageUiState? {
        if (event !is FilterEvent) return null

        val pipeline = state.pipeline
        return when (event) {
            is PreviewFilter -> {
                // 逻辑复用：获取 BaseImage
                val baseImage = if (event.forceReloadBaseImage || pipeline.draft.baseImage == null) {
                    pipeline.getInputImage(pipeline.activeIndex, state.project.activeImage)
                } else {
                    pipeline.draft.baseImage
                } ?: return state

                // 情况1：纯查看滤镜 (ViewFilter)，直接进入编辑状态
                if (event.filter is ViewFilter) {
                    val newPipeline = pipeline.updateDraft(activeFilter = event.filter, baseImage = baseImage)
                    state.update(newPipeline)
                }

                // 情况2：需要计算的滤镜
                pipelineUseCase.processSingle(baseImage, event.filter)
                    .map { result ->
                        val newPipeline = pipeline.updateDraft(result, event.filter, baseImage)
                        state.update(newPipeline)
                    }
                    .getOrElse {
                        showToast("预览失败: ${it.message}")
                        state
                    }
            }
            is UpdateCurrentStep -> {
                val newImage = state.pipeline.draft.previewImage
                if (state.pipeline.activeIndex <= 0 || newImage == null) {
                    showToast("无法更新：请先选择一个步骤并调整参数")
                    state
                } else {
                    pipelineUseCase.updateCurrentStep(state.pipeline, newImage)
                        .map { newPipeline ->
                            state.update(newPipeline)
                        }
                        .getOrElse {
                            showToast("更新失败: ${it.message}")
                            state
                        }
                }
            }
            is ApplyNewStep -> {
                val newImage = state.pipeline.draft.previewImage ?: return state

                val tempPipeline = pipeline.appendStep(newImage)
                val editStep = tempPipeline.editStep(
                    index = tempPipeline.steps.size,
                    filter = newImage.appliedFilter ?: ViewFilter,
                    baseImage = state.project.activeImage // 这里的 state 来自外部闭包，是合法的
                )
                state.update(editStep)
            }
            else -> state
        }
    }
}