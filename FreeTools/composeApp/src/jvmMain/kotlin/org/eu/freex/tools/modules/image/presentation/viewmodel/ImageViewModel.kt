package org.eu.freex.tools.modules.image.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
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
import org.eu.freex.tools.modules.image.application.ProjectUseCase
import org.eu.freex.tools.modules.image.domain.model.EditSession
import org.eu.freex.tools.modules.image.domain.model.Pipeline
import org.eu.freex.tools.modules.image.domain.repository.ImageRepository
import org.eu.freex.tools.modules.image.domain.service.FilterService
import org.eu.freex.tools.modules.image.domain.service.ProjectService
import org.eu.freex.tools.modules.image.domain.service.ResourceService
import org.eu.freex.tools.modules.image.presentation.core.ImageActionScope
import org.eu.freex.tools.modules.image.presentation.core.ImageUiEvent
import org.eu.freex.tools.modules.image.presentation.core.ImageUiState

class ImageViewModel(
    repository: ImageRepository
) : ViewModel(), ImageActionScope {

    // 1. State
    private val _uiState = MutableStateFlow(ImageUiState())
    override val state: ImageUiState get() = _uiState.value
    val uiState = _uiState.asStateFlow()

    private val _uiEffect = Channel<String>(Channel.BUFFERED)
    val uiEffect = _uiEffect.receiveAsFlow()

    // 2. Services (私有)
    private val filterService = FilterService(repository)
    private val projectService = ProjectService()
    private val resourceService = ResourceService(repository)

    // 3. UseCases (公开)
    override val pipelineUseCase = PipelineUseCase(filterService)
    override val projectUseCase = ProjectUseCase(projectService, resourceService)

    override val scope = viewModelScope
    override var filterPreviewJob: Job? = null

    init {
        observeProjectChanges()
    }

    // 监听源图变化，自动触发 UseCase
    private fun observeProjectChanges() {
        viewModelScope.launch {
            uiState.map { it.project.activeImage }
                .distinctUntilChanged()
                .collectLatest { sourceImage ->
                    if (sourceImage == null) {
                        setPipeline { Pipeline() }
                    } else {
                        openLoading()
                        pipelineUseCase.refreshPipeline(sourceImage, state.pipeline)
                            .onSuccess { newPipeline ->
                                setPipeline { newPipeline.copy(draft = EditSession()) }
                            }
                            .onFailure { showToast("同步失败: ${it.message}") }
                        closeLoading()
                    }
                }
        }
    }

    override fun setState(reducer: ImageUiState.() -> ImageUiState) {
        _uiState.update { it.reducer() }
    }

    override fun showToast(message: String) {
        viewModelScope.launch { _uiEffect.send(message) }
    }

    override fun launch(block: suspend ImageActionScope.() -> Unit) {
        viewModelScope.launch {
            openLoading()
            try {
                block()
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Error: ${e.message}")
            } finally {
                closeLoading()
            }
        }
    }

    override fun handleEvent(event: ImageUiEvent) {
        with(event) { execute() }
    }
}