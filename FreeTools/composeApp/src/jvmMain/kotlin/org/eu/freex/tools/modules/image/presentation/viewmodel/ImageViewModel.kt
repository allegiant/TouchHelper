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
import org.eu.freex.tools.modules.image.domain.model.WorkImage
import org.eu.freex.tools.modules.image.domain.repository.ImageRepository
import org.eu.freex.tools.modules.image.domain.service.FilterService
import org.eu.freex.tools.modules.image.domain.service.ProjectService
import org.eu.freex.tools.modules.image.domain.service.ResourceService
import org.eu.freex.tools.modules.image.domain.service.SegmentationService
import org.eu.freex.tools.modules.image.presentation.contract.ImageActionScope
import org.eu.freex.tools.modules.image.presentation.contract.ImageUiEvent
import org.eu.freex.tools.modules.image.presentation.contract.ImageUiState
import org.eu.freex.tools.modules.image.presentation.contract.model.DraftState
import org.eu.freex.tools.modules.image.presentation.contract.model.PipelineState

class ImageViewModel(repository: ImageRepository) : ViewModel(), ImageActionScope {

    // 1. 状态管理
    private val _uiState = MutableStateFlow(ImageUiState())
    override val state: ImageUiState get() = _uiState.value
    val uiState = _uiState.asStateFlow()

    // 2. 副作用通道
    private val _uiEffect = Channel<String>(Channel.BUFFERED)
    val uiEffect = _uiEffect.receiveAsFlow()

    // 3. 依赖注入
    override val filterService = FilterService(repository)
    override val resourceService = ResourceService(repository)
    override val segmentationService = SegmentationService(repository)
    override val projectService = ProjectService()

    override val scope = viewModelScope
    override var filterPreviewJob: Job? = null

    // --- 【新增】初始化块：启动状态监听 ---
    init {
        observeProjectChanges()
    }

    /**
     * 【核心协调逻辑】监听 Project 变化，自动驱动 Pipeline
     * 这就是你提到的 "第三方处理"：
     * 1. 监听源图变化
     * 2. 拿到 BufferImage
     * 3. 给流水线发送指令 (syncPipeline)
     */
    private fun observeProjectChanges() {
        viewModelScope.launch {
            // 使用 Flow 监听 currentSourceImage 的变化
            // distinctUntilChanged 保证只有真的换图了才触发
            uiState.map { it.project.currentSourceImage }
                .distinctUntilChanged()
                .collectLatest { sourceImage ->
                    // 触发流水线同步逻辑
                    syncPipeline(sourceImage)
                }
        }
    }

    private suspend fun syncPipeline(source: WorkImage?) {
        setSegmentation { copy(activeRects = emptyList()) }

        if (source == null) {
            setPipeline {
                // 重置所有状态
                PipelineState()
            }
            return
        }

        setUi { copy(isLoading = true) }

        val filters = state.pipeline.pipelineSteps.mapNotNull { it.appliedFilter }

        filterService.processChain(source, filters)
            .onSuccess { newSteps ->
                setPipeline {
                    copy(
                        pipelineSteps = newSteps,
                        selectedPipelineIndex = newSteps.size,
                        // 同步完成后，Draft 重置为 clean state
                        draft = DraftState()
                    )
                }
                setUi { copy(isLoading = false, rightPanelTabIndex = 0) }
            }
            .onFailure {
                it.printStackTrace()
                setUi { copy(isLoading = false) }
                showToast("流水线同步失败: ${it.message}")
            }
    }

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
            setUi { copy(isLoading = true) }
            try {
                block()
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("操作失败: ${e.message}")
            } finally {
                setUi { copy(isLoading = false) }
            }
        }
    }

    // 唯一的入口
    override fun handleEvent(event: ImageUiEvent) {
        with(event) {
            execute()
        }
    }
}