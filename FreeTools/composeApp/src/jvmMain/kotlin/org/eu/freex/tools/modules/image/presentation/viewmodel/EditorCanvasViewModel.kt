package org.eu.freex.tools.modules.image.presentation.viewmodel

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
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
import org.eu.freex.tools.common.model.PickingType
import org.eu.freex.tools.common.utils.toHexString
import org.eu.freex.tools.modules.image.application.ImageProcessingUseCase
import org.eu.freex.tools.modules.image.domain.model.FeaturePoint
import org.eu.freex.tools.modules.image.domain.model.ImageLayer
import org.eu.freex.tools.modules.image.domain.repository.ProjectRepository

data class EditorCanvasUiState(
    // 最终用于渲染的图片 (来自于 Pipeline 的结果)
    val displayImage: ImageLayer? = null,
    // 交互状态
    val pickingType: PickingType = PickingType.NONE,
    // [修改] 从 Boolean 改为持有具体的 Layer，不为 null 时即代表“正在裁剪模式”
    val cropperLayer: ImageLayer? = null,
    val featurePoints: List<FeaturePoint> = emptyList(),
    val searchRegion: IntRect? = null,
    // [新增/确认] 必须要有这两个属性来记录画布状态
    val scale: Float = 1f,
    val pan: Offset = Offset.Zero
)

class EditorCanvasViewModel(
    projectRepo: ProjectRepository,
    private val processingUseCase: ImageProcessingUseCase
) : ViewModel() {

    private val _interactionState = MutableStateFlow(EditorCanvasUiState())

    // 1. 用于广播一次性事件 (如取色结果、取点坐标)
    val pickEvent = MutableSharedFlow<Any>()

    val uiState: StateFlow<EditorCanvasUiState> = combine(
        projectRepo.workspace, // 监听 workspace 获取最新的 displayImage
        _interactionState
    ) { workspace, interaction ->
        interaction.copy(
            displayImage = workspace.displayImage
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        EditorCanvasUiState()
    )

    /**
     * 处理画布的变换 (缩放 + 平移)
     * @param zoomChange 缩放增量 (transformable 的回调值)
     * @param panChange 平移增量 (transformable 的回调值)
     */
    fun updateTransform(zoomChange: Float, panChange: Offset) {
        _interactionState.update { state ->
            // 计算新缩放值，限制在 0.1 ~ 10.0 之间
            val newScale = (state.scale * zoomChange).coerceIn(0.1f, 10f)
            // 计算新偏移值
            val newPan = state.pan + panChange
            state.copy(scale = newScale, pan = newPan)
        }
    }

    /**
     * 重置视图 (例如导入新图片时调用)
     */
    fun resetView() {
        _interactionState.update { it.copy(scale = 1f, pan = Offset.Zero) }
    }


    // [修改] 退出裁剪模式
    fun exitCropMode() {
        _interactionState.update { it.copy(cropperLayer = null) }
    }

    // [修改] 确认裁剪
    fun confirmCrop(rect: Rect) {
        // 获取当前正在裁的图
        val sourceLayer = uiState.value.cropperLayer ?: return

        viewModelScope.launch {
            processingUseCase.cropImage(sourceLayer, rect)
                .onSuccess {
                    exitCropMode() // 裁剪成功，关闭 Dialog
                }
        }
    }

    // --- 拾取交互 ---

    fun setPickingType(type: PickingType) {
        _interactionState.update { it.copy(pickingType = type, cropperLayer = null) }
    }

    // [新增] 处理画布点击 (由 View 层调用)
    fun onCanvasClick(offset: Offset, color: Color) {
        val pickingType = uiState.value.pickingType
        viewModelScope.launch {
            if (pickingType == PickingType.POINT) {
                // 发送坐标事件
                pickEvent.emit(IntOffset(offset.x.toInt(), offset.y.toInt()))
            } else if (pickingType == PickingType.COLOR) {
                // 发送颜色事件
                pickEvent.emit(color)
            }
        }
    }

    fun addFeaturePoint(x: Int, y: Int, color: Color) {
        val currentList = _interactionState.value.featurePoints
        // 1. 计算新序号 (当前数量 + 1)
        val newIndex = currentList.size + 1

        // 2. 将 Color 对象转换为 Hex 字符串 (#RRGGBB)
        val hexColor = color.toHexString()

        // 3. 构建新的 FeaturePoint
        val newPoint = FeaturePoint(
            index = newIndex,
            x = x,
            y = y,
            colorHex = hexColor,
            tolerance = "101010", // 默认偏色
            isChecked = true
        )

        // 4. 更新状态
        _interactionState.update { state ->
            state.copy(featurePoints = state.featurePoints + newPoint)
        }
    }

// === 建议新增以下方法，用于支持修改偏色和删除后重新排序 ===

    /**
     * 更新特征点 (用于修改偏色或备注)
     */
    fun updateFeaturePoint(id: String, newPoint: FeaturePoint) {
        _interactionState.update { state ->
            val updatedList = state.featurePoints.map {
                if (it.id == id) newPoint else it
            }
            state.copy(featurePoints = updatedList)
        }
    }

    /**
     * 删除特征点并重新计算序号 (保持序号连续 1,2,3...)
     */
    fun removeFeaturePoint(id: String) {
        _interactionState.update { state ->
            // 过滤掉要删除的点
            val filteredList = state.featurePoints.filter { it.id != id }

            // 重新建立索引 (Re-indexing)
            val reIndexedList = filteredList.mapIndexed { index, point ->
                point.copy(index = index + 1)
            }

            state.copy(featurePoints = reIndexedList)
        }
    }

    /**
     * 更新搜索区域
     */
    fun updateSearchRegion(x: Int, y: Int, w: Int, h: Int) {
        _interactionState.update { it.copy(searchRegion = IntRect(x, y, w, h)) }
    }

    /**
     * 清除搜索区域
     */
    fun clearSearchRegion() {
        _interactionState.update { it.copy(searchRegion = null) }
    }
}