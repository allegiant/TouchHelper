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
    private var workspace = ImageWorkspace()
    private val _uiState = MutableStateFlow(ImageUiState())

    override val uiState = _uiState.asStateFlow()
    override val scope = viewModelScope

    // --- Delegates ---
    // 按顺序初始化，某些 Delegate (如 Pipeline/Segmentation) 初始化时会启动协程
    private val assetDelegate = AssetDelegate(this)
    private val interactionDelegate = InteractionDelegate(this)
    private val pipelineDelegate = PipelineDelegate(this)
    private val segmentationDelegate = SegmentationDelegate(this)

    // --- Main Event Router ---
    fun handleEvent(event: ImageUiEvent) {
        viewModelScope.launch {
            // 1. Loading Indicator Logic
            if (shouldShowLoading(event)) {
                _uiState.update { it.copy(isLoading = true) }
            }

            // 2. Delegate Routing using Sealed Interfaces
            runCatching {
                when (event) {
                    is AssetEvent -> assetDelegate.handle(event)
                    is InteractionEvent -> interactionDelegate.handle(event)
                    is PipelineEvent -> pipelineDelegate.handle(event)
                    is SegmentationEvent -> segmentationDelegate.handle(event)
                }
            }.onFailure { it.printStackTrace() }

            // 3. Ensure UI consistency (Turn off loading)
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    // --- ViewModelContext Implementation ---

    override fun updateWorkspace(transform: (ImageWorkspace) -> ImageWorkspace) {
        workspace = transform(workspace)
        refreshUiState()
    }

    override fun getWorkspaceSnapshot(): ImageWorkspace = workspace

    override fun updateUiState(transform: (ImageUiState) -> ImageUiState) {
        _uiState.update(transform)
    }

    private fun refreshUiState() {
        _uiState.update {
            it.copy(
                assets = workspace.assets,
                activeChain = workspace.pipeline,
                segmentationProject = workspace.segmentation
            )
        }
    }

    // --- Exposed Helpers (For UI Components) ---

    suspend fun awaitColorPick(): Color? = interactionDelegate.awaitColorPick()
    suspend fun awaitPointPick(): IntOffset? = interactionDelegate.awaitPointPick()

    // --- Utility ---

    private fun shouldShowLoading(event: ImageUiEvent): Boolean {
        // 白名单机制：如果事件是交互型的或瞬时型的，不需要显示全屏 Loading
        return when (event) {
            is PreviewFilter,
            is TriggerColorPick,
            is TriggerPointPick,
            is CancelPick,
            is SwitchTab,
            is UpdateSegmentationConfig,
            is SelectChar,
            is StopLabeling -> false
            else -> true
        }
    }
}