package org.eu.freex.tools.modules.image.presentation.viewmodel

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.eu.freex.tools.common.state.BaseViewModel
import org.eu.freex.tools.modules.image.application.PipelineUseCase
import org.eu.freex.tools.modules.image.domain.model.EditSession
import org.eu.freex.tools.modules.image.domain.model.Pipeline
import org.eu.freex.tools.modules.image.presentation.core.ImageEventDispatcher
import org.eu.freex.tools.modules.image.presentation.core.ImageUiEvent
import org.eu.freex.tools.modules.image.presentation.core.ImageUiState

// 移除 : ImageActionScope
class ImageViewModel(
    imageEventDispatcher: ImageEventDispatcher,
    private val pipelineUseCase: PipelineUseCase
) : BaseViewModel<ImageUiEvent, ImageUiState>(
    initialState = ImageUiState(),
    dispatcher = imageEventDispatcher
) {

    init {
        observeProjectChanges()
    }

    private fun observeProjectChanges() {
        viewModelScope.launch {
            // 监听 activeImage 变化
            uiState.map { it.workspace.project.activeImage }
                .distinctUntilChanged()
                .collectLatest { sourceImage ->
                    if (sourceImage == null) {
                        // 图片为空，清空流水线
                        _uiState.update { it.update(Pipeline()) }
                    } else {
                        // 1. 开启 Loading (使用 it)
                        _uiState.update { it.updateLoading(true) }

                        // 2. 执行业务逻辑
                        // 注意：这里读取 state.workspace.pipeline 是没问题的，表示基于当前流水线刷新
                        val result = pipelineUseCase.refreshPipeline(
                            sourceImage,
                            state.workspace.pipeline
                        )

                        // 3. 统一处理结果 (闭环 Loading)
                        result.fold(
                            onSuccess = { newPipeline ->
                                _uiState.update {
                                    it.update(newPipeline.copy(draft = EditSession())) // 更新数据
                                        .updateLoading(false) // 链式调用，同时关闭 Loading
                                }
                            },
                            onFailure = { error ->
                                // 🔥 必须处理失败情况，否则 Loading 关不掉！
                                _uiState.update { it.updateLoading(false) }

                                // 可选：发送错误提示
                                sendEffect("刷新失败: ${error.message}")
                            }
                        )
                    }
                }
        }
    }
}