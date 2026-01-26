package org.eu.freex.tools.modules.image.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

data class SegmentationUiState(
    val project: SegmentationProject? = null,
    val selectedIndex: Int = -1,
    val isLabeling: Boolean = false
)

class SegmentationViewModel(
    private val projectRepo: ProjectRepository,
    private val segmentationUseCase: SegmentationUseCase
) : ViewModel() {

    private val _interactionState = MutableStateFlow(SegmentationUiState())

    val uiState: StateFlow<SegmentationUiState> = projectRepo.segmentation
        .combine(_interactionState) { project, interaction ->
            interaction.copy(project = project)
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            SegmentationUiState()
        )

    init {
        // [核心修复] 自动初始化逻辑
        viewModelScope.launch {
            val imageFlow = projectRepo.workspace.map { it.displayImage }.distinctUntilChanged()
            val configFlow = projectRepo.segmentation.map { it?.config }.distinctUntilChanged()

            combine(imageFlow, configFlow) { imageLayer, config ->
                // 只要有图，就应该跑切割
                if (imageLayer?.image != null) {
                    // [修复点] 如果 config 为 null (尚未初始化)，则使用默认配置
                    // 这样就能触发第一次运行，生成 Project，从而结束 Loading 状态
                    val safeConfig = config ?: SegmentationConfig()

                    // 只有当是 null (初始化) 或者 参数真的变了的时候才跑
                    // 这里依靠 distinctUntilChanged 过滤，但 combine 会在任意一个流变化时触发
                    // 简单的处理是：直接跑。UseCase 内部如果比较耗时，可以加 debounce 或者检查参数是否一致
                    segmentationUseCase.runSegmentation(safeConfig)
                }
            }.collect {
                // 保持流的激活
            }
        }
    }

    fun runSegmentation(config: SegmentationConfig) {
        viewModelScope.launch {
            segmentationUseCase.runSegmentation(config)
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

    fun submitLabel(text: String) {
        val currentProject = uiState.value.project ?: return
        val currentIndex = uiState.value.selectedIndex

        if (currentIndex != -1) {
            val newLabels = currentProject.labels.toMutableMap().apply { put(currentIndex, text) }

            projectRepo.updateWorkspace { ws ->
                val newProj = currentProject.copy(labels = newLabels)
                ws.copy(segmentation = newProj)
            }

            val nextIndex = (currentIndex + 1).coerceAtMost(currentProject.results.size - 1)
            _interactionState.update {
                it.copy(selectedIndex = nextIndex, isLabeling = true)
            }
        }
    }

    // [新增] 处理画布点击
    fun onCanvasTap(pixelX: Int, pixelY: Int) {
        // 1. 获取当前项目状态 (直接从 uiState 拿，而不是 _localProjectState)
        val currentProject = uiState.value.project ?: return
        val results = currentProject.results

        // 2. 查找命中的框
        // 倒序查找，优先选中最上层的
        val index = results.indexOfLast { rect ->
            // [注意] 请根据您的 SegmentationRect 定义确认属性名
            // 如果是 left/top，这里就用 left/top
            // 如果是 x/y，这里就用 x/y
            val rX = rect.left // 假设您的 Rect 用的是 left
            val rY = rect.top  // 假设您的 Rect 用的是 top
            val rW = rect.width.toInt()
            val rH = rect.height.toInt()

            pixelX >= rX &&
                    pixelX < (rX + rW) &&
                    pixelY >= rY &&
                    pixelY < (rY + rH)
        }

        // 3. 调用现有的方法更新选中状态
        selectRect(index)

        // 可选：调试日志
        println("DEBUG: 点击 ($pixelX, $pixelY), 命中索引: $index")
    }
}