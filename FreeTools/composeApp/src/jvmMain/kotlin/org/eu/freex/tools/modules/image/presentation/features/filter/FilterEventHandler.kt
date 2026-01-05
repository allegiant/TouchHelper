package org.eu.freex.tools.modules.image.presentation.features.filter

import org.eu.freex.tools.modules.image.application.PipelineUseCase
import org.eu.freex.tools.modules.image.domain.model.EditSession
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
        // 【自动过滤】
        if (event !is FilterEvent) return null

        return when (event) {
            is PreviewFilter -> {
                val pipeline = state.pipeline
                val baseImage = if (event.forceReloadBaseImage || pipeline.draft.baseImage == null) {
                    pipeline.getInputImage(pipeline.activeIndex, state.project.activeImage)
                } else {
                    pipeline.draft.baseImage
                } ?: return state

                if (event.filter is ViewFilter) {
                    val newDraft = EditSession(activeFilter = event.filter, baseImage = baseImage)
                    return state.copy(pipeline = pipeline.copy(draft = newDraft))
                }

                pipelineUseCase.processSingle(baseImage, event.filter)
                    .map { result ->
                        val newDraft = EditSession(activeFilter = event.filter, previewImage = result, baseImage = baseImage)
                        state.copy(pipeline = pipeline.copy(draft = newDraft))
                    }
                    .getOrElse {
                        showToast("预览失败: ${it.message}")
                        state
                    }
            }
            is UpdateCurrentStep -> {
                val newImage = state.pipeline.draft.previewImage
                if (state.pipeline.activeIndex <= 0 || newImage == null) {
                    showToast("无法更新")
                    state
                } else {
                    pipelineUseCase.updateCurrentStep(state.pipeline, newImage)
                        .map { state.copy(pipeline = it) }
                        .getOrElse {
                            showToast("更新失败: ${it.message}")
                            state
                        }
                }
            }
            is ApplyNewStep -> {
                val newImage = state.pipeline.draft.previewImage ?: return state
                var newPipeline = state.pipeline.appendStep(newImage)
                val newIndex = newPipeline.steps.size
                newPipeline = newPipeline.copy(activeIndex = newIndex)
                    .startEditing(newImage.appliedFilter ?: ViewFilter, state.project.activeImage)
                state.copy(pipeline = newPipeline)
            }

            else -> {
                throw Exception("Unhandled event: $event")
            }
        }
    }
}