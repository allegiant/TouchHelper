package org.eu.freex.tools.modules.image.presentation.features.pipeline

import org.eu.freex.tools.modules.image.application.PipelineUseCase
import org.eu.freex.tools.modules.image.domain.model.ViewFilter
import org.eu.freex.tools.modules.image.presentation.core.ImageEventHandler
import org.eu.freex.tools.modules.image.presentation.core.ImageUiEvent
import org.eu.freex.tools.modules.image.presentation.core.ImageUiState
import org.eu.freex.tools.modules.image.presentation.core.PipelineEvent
import java.lang.Exception

class PipelineEventHandler(
    private val pipelineUseCase: PipelineUseCase
) : ImageEventHandler {

    override suspend fun handle(
        event: ImageUiEvent,
        state: ImageUiState,
        showToast: (String) -> Unit
    ): ImageUiState? {
        if (event !is PipelineEvent) return null

        return when (event) {
            is SelectPipelineStep -> {
                val targetFilter = if (event.index == 0) ViewFilter else
                    state.pipeline.steps.getOrNull(event.index - 1)?.appliedFilter ?: ViewFilter

                // 语义化调用：进入编辑模式
                state.mapPipeline {
                    editStep(
                        index = event.index,
                        filter = targetFilter,
                        baseImage = state.project.activeImage // Pipeline 内部会自动处理 getInputImage，这里传原图备用
                    )
                }
            }
            is DeletePipelineStep -> {
                if (event.index <= 0) {
                    showToast("无法删除原图")
                    state
                } else {
                    pipelineUseCase.deleteStep(state.pipeline, state.project, event.index)
                        .map { newPipeline -> state.copy(pipeline = newPipeline) }
                        .getOrElse {
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