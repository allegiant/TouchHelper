package org.eu.freex.tools.modules.image.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.eu.freex.tools.modules.image.application.ImageProcessingUseCase
import org.eu.freex.tools.modules.image.domain.model.ImageFilter
import org.eu.freex.tools.modules.image.domain.model.ImageLayer
import org.eu.freex.tools.modules.image.domain.model.Pipeline
import org.eu.freex.tools.modules.image.domain.repository.ProjectRepository

data class PipelineUiState(
    val pipeline: Pipeline? = null,
    // 瞬态预览层（拖动滑块时生成的临时图片，不存入 Repository）
    val previewLayer: ImageLayer? = null
)

class PipelineViewModel(
    private val projectRepo: ProjectRepository,
    private val processingUseCase: ImageProcessingUseCase
) : ViewModel() {

    // 内部持有预览状态
    private val _previewState = MutableStateFlow<ImageLayer?>(null)

    val uiState: StateFlow<PipelineUiState> = combine(
        projectRepo.pipeline,
        _previewState
    ) { pipeline, preview ->
        PipelineUiState(
            pipeline = pipeline,
            previewLayer = preview
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        PipelineUiState()
    )

    private var previewJob: Job? = null

    // --- 滤镜操作 ---

    fun addFilter(filter: ImageFilter) {
        viewModelScope.launch {
            processingUseCase.addFilterStep(filter)
        }
    }

    fun removeFilter(index: Int) {
        viewModelScope.launch {
            processingUseCase.removeStep(index)
        }
    }

    // [新增] 选中流水线的某一步 (用于查看中间结果或从中间插入滤镜)
    fun selectStep(index: Int) {
        // 直接更新 Repository 中的 Pipeline 状态 (activeIndex)
        // Repository 发出新流 -> UI 自动更新 -> Canvas 渲染该步结果
        val currentPipeline = projectRepo.workspace.value.pipeline ?: return
        if (currentPipeline.activeIndex != index) {
            val newPipeline = currentPipeline.copy(activeIndex = index)
            projectRepo.updateWorkspace { it.copy(pipeline = newPipeline) }
        }
    }

    /**
     * 实时预览 (拖拽滑块时调用)
     * 使用防抖或直接计算，生成临时 ImageLayer
     */
    fun onFilterPreviewChange(filter: ImageFilter) {
        val currentPipeline = uiState.value.pipeline ?: return
        val currentAssets = projectRepo.workspace.value.assets
        val inputLayer = currentPipeline.getActiveLayer(currentAssets) ?: return

        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            // 简单的防抖，避免卡顿
            // delay(10)
            val preview = processingUseCase.calculatePreview(inputLayer, filter)
            _previewState.update { preview }
        }
    }

    /**
     * 确认修改 (松开鼠标时调用)
     * 将变更提交到 Repository
     */
    fun onFilterValueConfirmed(filter: ImageFilter) {
        previewJob?.cancel()
        _previewState.update { null } // 清除预览图
        viewModelScope.launch {
            processingUseCase.updateFilterStep(filter)
        }
    }
}