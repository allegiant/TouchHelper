package org.eu.freex.tools.modules.image.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.eu.freex.tools.modules.image.application.PipelineUseCase
import org.eu.freex.tools.modules.image.domain.model.EditSession
import org.eu.freex.tools.modules.image.domain.model.Pipeline
import org.eu.freex.tools.modules.image.presentation.core.ImageEventDispatcher
import org.eu.freex.tools.modules.image.presentation.core.ImageUiEvent
import org.eu.freex.tools.modules.image.presentation.core.ImageUiState

// 移除 : ImageActionScope
class ImageViewModel(
    private val imageEventDispatcher: ImageEventDispatcher,
    private val pipelineUseCase: PipelineUseCase
) : ViewModel() { // 只继承 ViewModel

    // State
    private val _uiState = MutableStateFlow(ImageUiState())
    val state: ImageUiState get() = _uiState.value // 保持 getter 以防 UI 还在用
    val uiState = _uiState.asStateFlow()

    // Effects
    private val _uiEffect = Channel<String>(Channel.BUFFERED)
    val uiEffect = _uiEffect.receiveAsFlow()

    init {
        observeProjectChanges()
    }

    private fun observeProjectChanges() {
        viewModelScope.launch {
            uiState.map { it.project.activeImage }
                .distinctUntilChanged()
                .collectLatest { sourceImage ->
                    if (sourceImage == null) {
                        _uiState.update { it.copy(pipeline = Pipeline()) }
                    } else {
                        _uiState.update { it.copy(isLoading = true) }
                        pipelineUseCase.refreshPipeline(sourceImage, state.pipeline)
                            .onSuccess { newPipeline ->
                                _uiState.update { it.copy(pipeline = newPipeline.copy(draft = EditSession())) }
                            }
                        _uiState.update { it.copy(isLoading = false) }
                    }
                }
        }
    }

    // --- 唯一的公共入口 ---
    fun handleEvent(event: ImageUiEvent) {
        viewModelScope.launch {
            // 统一 Loading
            _uiState.update { it.copy(isLoading = true) }

            val toast: (String) -> Unit = { msg ->
                viewModelScope.launch { _uiEffect.send(msg) }
            }

            // 自动分发
            val newState = imageEventDispatcher.dispatch(event, state, toast)

            _uiState.update { newState }

            _uiState.update { it.copy(isLoading = false) }
        }
    }
}