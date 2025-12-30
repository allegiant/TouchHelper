package org.eu.freex.tools.modules.image.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.eu.freex.tools.modules.image.domain.repository.ImageRepository
import org.eu.freex.tools.modules.image.domain.usecase.FilterProcessor
import org.eu.freex.tools.modules.image.domain.usecase.ProjectProcessor
import org.eu.freex.tools.modules.image.domain.usecase.ResourceProcessor
import org.eu.freex.tools.modules.image.domain.usecase.SegmentationProcessor
import org.eu.freex.tools.modules.image.presentation.contract.ImageActionScope
import org.eu.freex.tools.modules.image.presentation.contract.ImageUiEvent
import org.eu.freex.tools.modules.image.presentation.contract.ImageUiState

class ImageViewModel(
    private val repository: ImageRepository
) : ViewModel(), ImageActionScope {

    // 1. 状态管理
    private val _uiState = MutableStateFlow(ImageUiState())
    override val state: ImageUiState get() = _uiState.value
    val uiState = _uiState.asStateFlow()

    // 2. 副作用通道 (用于发送 Toast/Snackbar 消息)
    private val _uiEffect = Channel<String>(Channel.BUFFERED)
    val uiEffect = _uiEffect.receiveAsFlow()

    // 3. 依赖注入
    override val filterProcessor = FilterProcessor(repository)
    override val resourceProcessor = ResourceProcessor(repository)
    override val segmentationProcessor = SegmentationProcessor(repository)
    override val projectProcessor = ProjectProcessor()

    override val scope = viewModelScope
    override var filterPreviewJob: Job? = null


    override fun updateState(reducer: (ImageUiState) -> ImageUiState) {
        _uiState.update(reducer)
    }

    override fun showToast(message: String) {
        viewModelScope.launch { _uiEffect.send(message) }
    }

    override fun launch(block: suspend ImageActionScope.() -> Unit) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            try {
                block()
            } catch (e: Exception) {
                e.printStackTrace()
                updateState { it.copy(isLoading = false) }
                showToast("操作失败: ${e.message}")
            }
        }
    }

    // --- 4. 唯一的入口 ---
    fun handleEvent(event: ImageUiEvent) {
        event.execute(this)
    }
}