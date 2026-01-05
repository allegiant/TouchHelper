package org.eu.freex.tools.modules.image.presentation.features.pipeline

import org.eu.freex.tools.modules.image.application.PipelineUseCase
import org.eu.freex.tools.modules.image.presentation.core.ImageEventHandler
import org.eu.freex.tools.modules.image.presentation.core.ImageUiEvent
import org.eu.freex.tools.modules.image.presentation.core.ImageUiState
import org.eu.freex.tools.modules.image.presentation.core.PipelineEvent
import org.eu.freex.tools.modules.image.presentation.core.commitTo

class PipelineEventHandler(
    private val pipelineUseCase: PipelineUseCase
) : ImageEventHandler {

    override suspend fun handle(
        event: ImageUiEvent,
        state: ImageUiState,
        showToast: (String) -> Unit
    ): ImageUiState? {
        // 自动过滤非流水线事件
        if (event !is PipelineEvent) return null

        return with(state) {
            when (event) {
                is SelectPipelineStep -> {
                    return pipeline
                        .activateStep(event.index, project.activeImage)
                        .commitTo(state)
                }

                is DeletePipelineStep -> {
                    if (event.index <= 0) {
                        showToast("无法删除原图")
                        state
                    } else {
                        // 调用 UseCase 处理复杂的删除重算逻辑
                        pipelineUseCase.deleteStep(pipeline, project, event.index)
                            .map { newPipeline ->
                                newPipeline.commitTo(state)
                            }
                            .getOrElse {
                                // 失败：报错并保持原状
                                showToast("删除失败: ${it.message}")
                                state
                            }
                    }
                }

                else -> {
                    state
                }
            }
        }
    }
}