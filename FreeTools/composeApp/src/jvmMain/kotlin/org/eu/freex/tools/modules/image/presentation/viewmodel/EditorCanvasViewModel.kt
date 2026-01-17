package org.eu.freex.tools.modules.image.presentation.viewmodel

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.eu.freex.tools.common.model.PickingType
import org.eu.freex.tools.modules.image.application.ImageProcessingUseCase
import org.eu.freex.tools.modules.image.domain.model.ImageLayer
import org.eu.freex.tools.modules.image.domain.repository.ProjectRepository

data class EditorCanvasUiState(
    // 最终用于渲染的图片 (来自于 Pipeline 的结果)
    val displayImage: ImageLayer? = null,
    // 交互状态
    val pickingType: PickingType = PickingType.NONE,
    // [修改] 从 Boolean 改为持有具体的 Layer，不为 null 时即代表“正在裁剪模式”
    val cropperLayer: ImageLayer? = null
)

class EditorCanvasViewModel(
    private val projectRepo: ProjectRepository,
    private val processingUseCase: ImageProcessingUseCase
) : ViewModel() {

    private val _interactionState = MutableStateFlow(EditorCanvasUiState())

    // [新增] 用于向外发送拾取结果的事件流
    private val _pickEvent = MutableSharedFlow<Any>() // Color or Offset
    val pickEvent = _pickEvent.asSharedFlow()

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
    // [修改] 进入裁剪模式 (需要传入要裁的图)
    fun enterCropMode(layer: ImageLayer) {
        _interactionState.update { it.copy(cropperLayer = layer, pickingType = PickingType.NONE) }
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
        val type = uiState.value.pickingType
        viewModelScope.launch {
            when (type) {
                PickingType.COLOR -> {
                    _pickEvent.emit(color)
                    // 拾取一次后通常自动退出模式
                    setPickingType(PickingType.NONE)
                }
                PickingType.POINT -> {
                    // 需要转换为 IntOffset
                    _pickEvent.emit(IntOffset(offset.x.toInt(), offset.y.toInt()))
                    setPickingType(PickingType.NONE)
                }
                else -> {
                    // 普通点击，可能是取消裁剪或选中切割框，视具体需求
                }
            }
        }
    }
}