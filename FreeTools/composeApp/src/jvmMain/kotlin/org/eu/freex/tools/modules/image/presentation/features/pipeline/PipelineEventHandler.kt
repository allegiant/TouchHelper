package org.eu.freex.tools.modules.image.presentation.features.pipeline

import org.eu.freex.tools.modules.image.application.PipelineUseCase
import org.eu.freex.tools.modules.image.domain.model.ViewFilter
import org.eu.freex.tools.modules.image.presentation.core.ImageEventHandler
import org.eu.freex.tools.modules.image.presentation.core.ImageUiEvent
import org.eu.freex.tools.modules.image.presentation.core.ImageUiState
import org.eu.freex.tools.modules.image.presentation.core.PipelineEvent

class PipelineEventHandler(
    private val pipelineUseCase: PipelineUseCase
) : ImageEventHandler {

    override suspend fun handle(
        event: ImageUiEvent, state: ImageUiState, showToast: (String) -> Unit
    ): ImageUiState? {
        // 1. 【自动过滤】如果不属于流水线事件，直接返回 null，交给下一个 Handler
        if (event !is PipelineEvent) return null

        // 2. 处理逻辑
        return when (event) {
            is SelectPipelineStep -> {
                val targetFilter =
                    if (event.index == 0) ViewFilter else state.pipeline.steps.getOrNull(event.index - 1)?.appliedFilter
                        ?: ViewFilter
                val newPipeline =
                    state.pipeline.copy(activeIndex = event.index).startEditing(targetFilter, state.project.activeImage)

                state.copy(pipeline = newPipeline)
            }

            is DeletePipelineStep -> {
                if (event.index <= 0) {
                    showToast("无法删除原图")
                    state
                } else {
                    pipelineUseCase.deleteStep(state.pipeline, state.project, event.index)
                        .map { newPipeline -> state.copy(pipeline = newPipeline) }.getOrElse {
                            showToast("删除失败: ${it.message}")
                            state
                        }
                }
            }

            else -> {
                throw Exception("Unhandled event: $event")
            }
        }
    }
}