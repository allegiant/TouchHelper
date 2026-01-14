package org.eu.freex.tools.modules.image.presentation.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.eu.freex.tools.modules.image.application.WorkspaceUseCase
import org.eu.freex.tools.modules.image.domain.model.ImageWorkspace
import org.eu.freex.tools.modules.image.presentation.core.*

class ImageViewModel(
    override val useCase: WorkspaceUseCase
) : ViewModel(), ViewModelContext {

    // --- State ---
    // [修改 1] 将 workspace 升级为 MutableStateFlow，作为事实来源
    private val _workspace = MutableStateFlow(ImageWorkspace())

    val workspace = _workspace.asStateFlow()

    private val _uiState = MutableStateFlow(ImageUiState())
    override val uiState = _uiState.asStateFlow()

    override val scope = viewModelScope

    // --- Delegates ---
    private val assetDelegate = AssetDelegate(this)
    private val interactionDelegate = InteractionDelegate(this)
    private val pipelineDelegate = PipelineDelegate(this)
    private val segmentationDelegate = SegmentationDelegate(this)

    // [修改 3] 去掉 private 修饰符，供 App.kt 调用 (修复无法访问 delegate 的问题)
    val fontLibraryDelegate = FontLibraryDelegate(this)

    // --- Main Event Router ---
    fun handleEvent(event: ImageUiEvent) {
        viewModelScope.launch {
            if (shouldShowLoading(event)) {
                _uiState.update { it.copy(isLoading = true) }
            }
            runCatching {
                when (event) {
                    is AssetEvent -> assetDelegate.handle(event)
                    is InteractionEvent -> interactionDelegate.handle(event)
                    is PipelineEvent -> pipelineDelegate.handle(event)
                    is SegmentationEvent -> segmentationDelegate.handle(event)
                    is AddToLibrary -> {
                        // 获取当前用来做切割的原图
                        val sourceImage = uiState.value.displayImage?.image
                        if (sourceImage != null) {
                            fontLibraryDelegate.addToLibrary(event.rect, sourceImage, event.label)
                        }
                    }
                }
            }.onFailure { it.printStackTrace() }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    // --- ViewModelContext Implementation ---

    override fun updateWorkspace(transform: (ImageWorkspace) -> ImageWorkspace) {
        // [修改 4] 使用 update 更新 StateFlow
        _workspace.update(transform)
        refreshUiState()
    }

    // [修改 5] 获取快照时取 value
    override fun getWorkspaceSnapshot(): ImageWorkspace = _workspace.value

    override fun updateUiState(transform: (ImageUiState) -> ImageUiState) {
        _uiState.update(transform)
    }

    private fun refreshUiState() {
        // [修改 6] 从 _workspace.value 获取最新数据同步给 uiState
        val currentWs = _workspace.value
        _uiState.update {
            it.copy(
                assets = currentWs.assets,
                activeChain = currentWs.pipeline,
                segmentationProject = currentWs.segmentation
            )
        }
    }

    // --- Exposed Helpers (For UI Components) ---

    suspend fun awaitColorPick(): Color? = interactionDelegate.awaitColorPick()
    suspend fun awaitPointPick(): IntOffset? = interactionDelegate.awaitPointPick()

    // --- Utility ---

    private fun shouldShowLoading(event: ImageUiEvent): Boolean {
        return when (event) {
            is PreviewFilter,
            is TriggerColorPick,
            is TriggerPointPick,
            is CancelPick,
            is SwitchTab,
            is UpdateSegmentationConfig,
            is SelectChar,
            is SubmitLabelAndNext,
            is StopLabeling -> false
            else -> true
        }
    }
}