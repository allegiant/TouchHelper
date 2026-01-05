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

        return when (event) {
            is PreviewFilter -> {
                val pipeline = state.pipeline
                // 逻辑复用：获取 BaseImage
                val baseImage = if (event.forceReloadBaseImage || pipeline.draft.baseImage == null) {
                    pipeline.getInputImage(pipeline.activeIndex, state.project.activeImage)
                } else {
                    pipeline.draft.baseImage
                } ?: return state

                // 情况1：纯查看滤镜 (ViewFilter)，直接进入编辑状态
                if (event.filter is ViewFilter) {
                    return state.mapPipeline {
                        // 语义化调用：更新草稿的滤镜和底图
                        updateDraft(activeFilter = event.filter, baseImage = baseImage)
                    }
                }

                // 情况2：需要计算的滤镜
                pipelineUseCase.processSingle(baseImage, event.filter)
                    .map { result ->
                        // 语义化调用：只更新预览图和滤镜
                        state.mapPipeline {
                            updateDraft(previewImage = result, activeFilter = event.filter, baseImage = baseImage)
                        }
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
                        .map { newPipeline -> state.copy(pipeline = newPipeline) }
                        .getOrElse {
                            showToast("更新失败: ${it.message}")
                            state
                        }
                }
            }
            is ApplyNewStep -> {
                val newImage = state.pipeline.draft.previewImage ?: return state
                // 语义化调用：追加步骤
                // 注意：appendStep 内部已经处理了指针移动和草稿重置，
                // 但为了连续编辑体验，我们可能希望追加后立即进入新步骤的编辑模式：
                val tempPipeline = state.pipeline.appendStep(newImage)

                state.copy(pipeline = tempPipeline.editStep(
                    index = tempPipeline.steps.size,
                    filter = newImage.appliedFilter ?: ViewFilter,
                    baseImage = state.project.activeImage // 这里简化处理，实际可能需要上一步的图
                ))
            }

            else -> {
                throw Exception("Unhandled event: $event")
            }
        }
    }
}