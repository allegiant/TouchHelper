package org.eu.freex.tools.modules.image.presentation.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntRect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.eu.freex.tools.common.model.PickEvent
import org.eu.freex.tools.common.model.PickingToolState
import org.eu.freex.tools.common.model.WorkbenchTab
import org.eu.freex.tools.common.utils.toHexString
import org.eu.freex.tools.modules.image.application.ImageProcessingUseCase
import org.eu.freex.tools.modules.image.domain.model.FeaturePoint
import org.eu.freex.tools.modules.image.domain.model.ImageLayer
import org.eu.freex.tools.modules.image.domain.model.ImageWorkspace
import org.eu.freex.tools.modules.image.domain.repository.ProjectRepository

// 定义业务状态
data class ImageWorkbenchUiState(
    val displayImage: ImageLayer? = null,
    val currentTab: WorkbenchTab = WorkbenchTab.FILTER,
    val activeTool: PickingToolState = PickingToolState.None,
    val isCropping: Boolean = false,

    val searchRegion: IntRect? = null,
    val cropperLayer: ImageLayer? = null,
)

class ImageWorkbenchViewModel(
    projectRepo: ProjectRepository,
    private val processingUseCase: ImageProcessingUseCase
) : ViewModel() {

    // 内部状态
    private val _currentTab = MutableStateFlow(WorkbenchTab.FILTER)
    private val _isSelectingRegion = MutableStateFlow(false)
    private val _cropperLayer = MutableStateFlow<ImageLayer?>(null)
    private val _featurePoints = MutableStateFlow<List<FeaturePoint>>(emptyList())
    private val _searchRegion = MutableStateFlow<IntRect?>(null)
    private val _activeTool = MutableStateFlow<PickingToolState>(PickingToolState.None)

    // 事件流 (用于通知 ToolRegistry)
    val pickEvent = MutableSharedFlow<PickEvent>()

    // 组合 UI 状态
    val uiState: StateFlow<ImageWorkbenchUiState> = combine(
        projectRepo.workspace,
        _currentTab,
        _activeTool,
        _isSelectingRegion,
        _featurePoints,
        _searchRegion,
        _cropperLayer
    ) { args ->
        val workspace = args[0] as ImageWorkspace
        ImageWorkbenchUiState(
            displayImage = workspace.displayImage,
            currentTab = args[1] as WorkbenchTab,
            activeTool = args[2] as PickingToolState,
            isCropping = args[3] as Boolean,
            searchRegion = args[5] as IntRect?,
            cropperLayer = args[6] as ImageLayer?
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ImageWorkbenchUiState())

    // === 业务逻辑操作 ===

    fun setTab(tab: WorkbenchTab) {
        _currentTab.update { tab }
    }

    fun activeTool(tool: PickingToolState) {
        _activeTool.value = tool
        if (tool !is PickingToolState.None) {
            _isSelectingRegion.value = false
        }
    }

    // --- Crop Logic ---
    fun startCropMode() {
        val currentLayer = uiState.value.displayImage
        if (currentLayer != null) {
            _cropperLayer.value = currentLayer
            _isSelectingRegion.value = true
            _activeTool.value = PickingToolState.None
        }
    }

    fun exitCropMode() {
        _isSelectingRegion.value = false
        _cropperLayer.value = null
    }

    fun confirmCrop(rect: IntRect) {
        val sourceLayer = _cropperLayer.value ?: return
        viewModelScope.launch {
            processingUseCase.cropImage(sourceLayer, rect).onSuccess { exitCropMode() }
        }
    }

    // --- Event Logic ---
    fun pickColor(color: Color) {
        viewModelScope.launch {
            pickEvent.emit(PickEvent.ColorPicked(0,0, color, color.toHexString()))
        }
    }

    fun pickPoint(x: Int, y: Int) {
        viewModelScope.launch { pickEvent.emit(PickEvent.PointPicked(x, y)) }
    }



    fun updateSearchRegion(x: Int, y: Int, w: Int, h: Int) {
        _searchRegion.value = IntRect(x, y, w, h)
    }

    fun clearSearchRegion() {
        _searchRegion.value = null
    }
}