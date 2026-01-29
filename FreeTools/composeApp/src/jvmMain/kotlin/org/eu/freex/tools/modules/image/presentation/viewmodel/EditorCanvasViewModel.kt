/* file: EditorCanvasViewModel.kt */
package org.eu.freex.tools.modules.image.presentation.viewmodel

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.eu.freex.tools.common.model.PickingType
import org.eu.freex.tools.common.model.WorkbenchTab
import org.eu.freex.tools.common.utils.toHexString
import org.eu.freex.tools.modules.image.application.ImageProcessingUseCase
import org.eu.freex.tools.modules.image.domain.model.FeaturePoint
import org.eu.freex.tools.modules.image.domain.model.ImageLayer
import org.eu.freex.tools.modules.image.domain.model.ImageWorkspace
import org.eu.freex.tools.modules.image.domain.repository.ProjectRepository

data class EditorCanvasTransform(
    val scale: Float = 1f,
    val pan: Offset = Offset.Zero
)

data class EditorCanvasUiState(
    val displayImage: ImageLayer? = null,
    // [修改] 解耦状态：保留 Tab 和 PickingType 独立存在
    val currentTab: WorkbenchTab? = null,
    val pickingType: PickingType = PickingType.NONE,
    val isCropping: Boolean = false,

    // 数据层
    val featurePoints: List<FeaturePoint> = emptyList(),
    val searchRegion: IntRect? = null,
    val cropperLayer: ImageLayer? = null
)

class EditorCanvasViewModel(
    projectRepo: ProjectRepository,
    private val processingUseCase: ImageProcessingUseCase
) : ViewModel() {

    private val _currentTab = MutableStateFlow<WorkbenchTab?>(null)
    private val _isSelectingRegion = MutableStateFlow(false)
    private val _pickingType = MutableStateFlow(PickingType.NONE)
    private val _cropperLayer = MutableStateFlow<ImageLayer?>(null)
    private val _featurePoints = MutableStateFlow<List<FeaturePoint>>(emptyList())
    private val _searchRegion = MutableStateFlow<IntRect?>(null)

    private val _transformState = MutableStateFlow(EditorCanvasTransform())
    val transformState: StateFlow<EditorCanvasTransform> = _transformState.asStateFlow()

    val pickEvent = MutableSharedFlow<Any>()

    val uiState: StateFlow<EditorCanvasUiState> = combine(
        projectRepo.workspace,      // args[0]
        _currentTab,                // args[1]
        _isSelectingRegion,         // args[2]
        _pickingType,               // args[3]
        _featurePoints,             // args[4]
        _searchRegion,              // args[5]
        _cropperLayer               // args[6]
    ) { args ->
        val workspace = args[0] as ImageWorkspace
        val tab = args[1] as? WorkbenchTab
        val isCrop = args[2] as Boolean
        val pickType = args[3] as PickingType
        @Suppress("UNCHECKED_CAST")
        val points = args[4] as List<FeaturePoint>
        val region = args[5] as IntRect?
        val cropLayer = args[6] as ImageLayer?

        // [修改] 不再计算单一的 EditorMode，而是直接透传状态
        // 让 View 层根据 (Tab + PickingType) 的组合来决定渲染和行为
        EditorCanvasUiState(
            displayImage = workspace.displayImage,
            currentTab = tab,
            pickingType = pickType,
            isCropping = isCrop,
            featurePoints = points,
            searchRegion = region,
            cropperLayer = cropLayer
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        EditorCanvasUiState()
    )

    // === View Interactions ===

    fun setTab(tab: WorkbenchTab) {
        _currentTab.update { tab }
    }

    fun updateTransform(zoomChange: Float, panChange: Offset) {
        _transformState.update { state ->
            val newScale = (state.scale * zoomChange).coerceIn(0.1f, 10f)
            val newPan = state.pan + panChange
            state.copy(scale = newScale, pan = newPan)
        }
    }

    // --- Crop ---
    fun startCropMode() {
        val currentLayer = uiState.value.displayImage
        if (currentLayer != null) {
            _cropperLayer.value = currentLayer
            _isSelectingRegion.value = true
            _pickingType.value = PickingType.NONE
        }
    }

    fun exitCropMode() {
        _isSelectingRegion.value = false
        _cropperLayer.value = null
    }

    fun confirmCrop(rect: IntRect) {
        val sourceLayer = _cropperLayer.value ?: return
        viewModelScope.launch {
            processingUseCase.cropImage(sourceLayer, rect)
                .onSuccess { exitCropMode() }
        }
    }

    // --- Picking ---
    fun setPickingType(type: PickingType) {
        _pickingType.value = type
        if (type != PickingType.NONE) {
            _isSelectingRegion.value = false
        }
    }

    fun pickColor(color: Color) {
        viewModelScope.launch { pickEvent.emit(color) }
    }

    fun pickPoint(x: Int, y: Int) {
        viewModelScope.launch { pickEvent.emit(IntOffset(x, y)) }
    }

    // --- Features ---
    fun addFeaturePoint(x: Int, y: Int, color: Color) {
        val currentList = _featurePoints.value
        val newIndex = currentList.size + 1
        val newPoint = FeaturePoint(
            index = newIndex,
            x = x,
            y = y,
            colorHex = color.toHexString(),
            tolerance = "101010",
            isChecked = true
        )
        _featurePoints.update { it + newPoint }
    }

    // --- Utils ---
    fun updateFeaturePoint(id: String, newPoint: FeaturePoint) {
        _featurePoints.update { list ->
            list.map { if (it.id == id) newPoint else it }
        }
    }

    fun removeFeaturePoint(id: String) {
        _featurePoints.update { list ->
            val filtered = list.filter { it.id != id }
            filtered.mapIndexed { index, point -> point.copy(index = index + 1) }
        }
    }

    fun updateSearchRegion(x: Int, y: Int, w: Int, h: Int) {
        _searchRegion.value = IntRect(x, y, w, h)
    }

    fun clearSearchRegion() {
        _searchRegion.value = null
    }
}