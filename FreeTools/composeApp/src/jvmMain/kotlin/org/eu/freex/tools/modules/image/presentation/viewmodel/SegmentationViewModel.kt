package org.eu.freex.tools.modules.image.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.eu.freex.tools.modules.image.application.SegmentationUseCase
import org.eu.freex.tools.modules.image.domain.model.SegmentationConfig
import org.eu.freex.tools.modules.image.domain.model.SegmentationProject
import org.eu.freex.tools.modules.image.domain.repository.ProjectRepository

// 切割专属 UI 状态，包含交互状态
data class SegmentationUiState(
    val project: SegmentationProject? = null,
    val selectedIndex: Int = -1,    // 当前选中的切割框索引
    val isLabeling: Boolean = false // 是否正在弹出标注对话框
)

class SegmentationViewModel(
    private val projectRepo: ProjectRepository,
    private val segmentationUseCase: SegmentationUseCase
) : ViewModel() {

    private val _interactionState = MutableStateFlow(SegmentationUiState())

    // [修复] 使用 extension syntax (.combine) 避免重载冲突
    val uiState: StateFlow<SegmentationUiState> = projectRepo.segmentation
        .combine(_interactionState) { project, interaction ->
            interaction.copy(project = project)
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            SegmentationUiState()
        )

    // [新增] 恢复响应式逻辑：当底图或配置变化时，自动运行切割
    init {
        viewModelScope.launch {
            // 监听 displayImage 和 config 的变化
            // 这里我们不需要 combine 的结果，只需要它的副作用(触发 runSegmentation)
            // 或者是单纯的 collect

            val imageFlow = projectRepo.workspace.map { it.displayImage }.distinctUntilChanged()
            // 注意：segmentation 是 Flow<SegmentationProject?>，我们需要从中取 config
            val configFlow = projectRepo.segmentation.map { it?.config }.distinctUntilChanged()

            combine(imageFlow, configFlow) { imageLayer, config ->
                if (imageLayer?.image != null && config != null) {
                    segmentationUseCase.runSegmentation(config)
                }
            }.collect {
                // 保持流的运行，这里不需要处理 emit 结果，因为 runSegmentation 是副作用
            }
        }
    }

    fun runSegmentation(config: SegmentationConfig) {
        viewModelScope.launch {
            segmentationUseCase.runSegmentation(config)
                .onFailure {
                    // 错误处理
                }
        }
    }

    fun selectRect(index: Int) {
        _interactionState.update { it.copy(selectedIndex = index) }
    }

    fun showLabelDialog() {
        if (uiState.value.selectedIndex != -1) {
            _interactionState.update { it.copy(isLabeling = true) }
        }
    }

    fun dismissLabelDialog() {
        _interactionState.update { it.copy(isLabeling = false) }
    }

    // [新增] 提交标注并自动跳转下一个
    fun submitLabel(text: String) {
        val currentProject = uiState.value.project ?: return
        val currentIndex = uiState.value.selectedIndex

        if (currentIndex != -1) {
            // 1. 更新标注 map
            val newLabels = currentProject.labels.toMutableMap().apply { put(currentIndex, text) }

            // 2. 更新 Repository
            projectRepo.updateWorkspace { ws ->
                val newProj = currentProject.copy(labels = newLabels)
                ws.copy(segmentation = newProj)
            }

            // 3. 自动跳转下一个
            val nextIndex = (currentIndex + 1).coerceAtMost(currentProject.results.size - 1)
            // 更新本地交互状态
            _interactionState.update {
                it.copy(selectedIndex = nextIndex, isLabeling = true)
            }
        }
    }
}