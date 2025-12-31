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

    // 2. 副作用通道
    private val _uiEffect = Channel<String>(Channel.BUFFERED)
    val uiEffect = _uiEffect.receiveAsFlow()

    // 3. 依赖注入
    override val filterProcessor = FilterProcessor(repository)
    override val resourceProcessor = ResourceProcessor(repository)
    override val segmentationProcessor = SegmentationProcessor(repository)
    override val projectProcessor = ProjectProcessor()

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

    /**
     * 执行流水线同步
     * (对应之前的 SyncPipelineSource 事件逻辑)
     */
    private suspend fun syncPipeline(source: WorkImage?) {
        // 1. 重置分割状态 (换图了，旧的框失效)
        setSegmentation { copy(activeRects = emptyList()) }

        // Case A: 没图了 (被删光了)
        if (source == null) {
            setPipeline {
                copy(pipelineSteps = emptyList(), selectedPipelineIndex = 0, currentImage = null)
            }
            return
        }

        // Case B: 有图 -> 拿着图去跑滤镜链
        // 自动开启 Loading (复用 launch 的逻辑或者手动控制)
        setUi { copy(isLoading = true) }

        val filters = state.pipeline.pipelineSteps.mapNotNull { it.appliedFilter }

        filterProcessor.processChain(source, filters)
            .onSuccess { newSteps ->
                // 更新流水线状态 -> 这会自动触发 "工作预览区" 和 "右侧功能区" 的刷新
                setPipeline {
                    copy(
                        pipelineSteps = newSteps,
                        selectedPipelineIndex = newSteps.size,
                        currentImage = null
                    )
                }
                // 【新增】根据你的需求：切换到功能区并回显
                // 假设 Tab 0 是滤镜调节区，如果有滤镜，我们可能想让用户看到参数
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
    fun handleEvent(event: ImageUiEvent) {
        with(event) {
            execute()
        }
    }
}