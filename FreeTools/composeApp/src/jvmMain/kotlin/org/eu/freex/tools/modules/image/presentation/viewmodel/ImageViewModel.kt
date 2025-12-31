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

class ImageViewModel(repository: ImageRepository) : ViewModel(), ImageActionScope {

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

    // 实现 setState
    override fun setState(reducer: ImageUiState.() -> ImageUiState) {
        _uiState.update { it.reducer() }
    }

    override fun showToast(message: String) {
        viewModelScope.launch { _uiEffect.send(message) }
    }

    // 实现全自动 launch
    override fun launch(block: suspend ImageActionScope.() -> Unit) {
        viewModelScope.launch {
            // 1. 自动开 Loading (注意是在 setUi 里)
            setUi { copy(isLoading = true) }
            try {
                // 2. 执行业务
                block()
            } catch (e: Exception) {
                // 3. 统一错误处理
                e.printStackTrace()
                showToast("操作失败: ${e.message}")
            } finally {
                // 4. 自动关 Loading
                setUi { copy(isLoading = false) }
            }
        }
    }

    // --- 4. 唯一的入口 ---
    fun handleEvent(event: ImageUiEvent) {
        // 调用时，让 event 在 'this' (ViewModel/Scope) 上执行
        with(event) {
            execute()
        }
    }
}