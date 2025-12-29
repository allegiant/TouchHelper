package org.eu.freex.tools.modules.image.presentation.viewmodel

import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope // 必须导入
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.eu.freex.tools.modules.image.data.repository.ImageRepositoryImpl
import org.eu.freex.tools.modules.image.data.source.RustDataSource
import org.eu.freex.tools.modules.image.presentation.contract.ImageUiEvent
import org.eu.freex.tools.modules.image.presentation.contract.ImageUiState
import org.eu.freex.tools.utils.ImageUtils

class ImageViewModel : ViewModel() {

    // 1. 状态管理
    private val _uiState = MutableStateFlow(ImageUiState())
    val uiState = _uiState.asStateFlow()

    // 2. 副作用通道 (用于发送 Toast/Snackbar 消息)
    private val _uiEffect = Channel<String>(Channel.BUFFERED)
    val uiEffect = _uiEffect.receiveAsFlow()

    // 3. 依赖注入
    private val repository = ImageRepositoryImpl(RustDataSource())

    // 4. 初始化子处理器
    // 使用 viewModelScope 替代手动创建的 scope
    // 传入 showToast 回调给 Handler 使用
    private val resourceHandler = ResourceHandler(viewModelScope, repository, _uiState, ::showToast)
    private val filterHandler = FilterHandler(viewModelScope, repository, _uiState, ::showToast)
    private val segmentationHandler = SegmentationHandler(viewModelScope, repository, _uiState, ::showToast)

    fun handleEvent(event: ImageUiEvent) {
        when (event) {
            // --- 资源与截图 ---
            is ImageUiEvent.LoadFile -> resourceHandler.loadFile(event)
            is ImageUiEvent.SelectSourceImage -> resourceHandler.selectSource(event.index)
            is ImageUiEvent.RemoveSourceImage -> resourceHandler.removeSource(event.index)
            is ImageUiEvent.StartScreenCapture -> resourceHandler.startCapture()
            is ImageUiEvent.ConfirmScreenCrop -> resourceHandler.saveScreenCapture(event.image)

            // --- 滤镜与流水线 ---
            is ImageUiEvent.ApplyCurrentFilter -> filterHandler.applyFilterAsNewStep()
            is ImageUiEvent.ModifyCurrentStep -> filterHandler.modifyCurrentStep()
            is ImageUiEvent.UpdateThreshold -> {
                _uiState.update { it.copy(thresholdRange = event.range) }
                filterHandler.triggerStepUpdate()
            }
            is ImageUiEvent.ToggleRgbAvg -> {
                _uiState.update { it.copy(isRgbAvgEnabled = event.enabled) }
                filterHandler.triggerStepUpdate()
            }
            is ImageUiEvent.SelectFilter -> _uiState.update { it.copy(currentFilter = event.filter) }
            is ImageUiEvent.SelectPipelineStep -> _uiState.update { it.copy(selectedPipelineIndex = event.index) }
            // 删除流水线步骤暂时没有 Handler 实现，如果需要可以加在 FilterHandler
            is ImageUiEvent.DeletePipelineStep -> {
                filterHandler.deletePipelineStep(event.index)
            }

            // --- 切割与规则 ---
            is ImageUiEvent.PerformSegmentation -> segmentationHandler.performSegmentation()
            is ImageUiEvent.UpdateGridParams -> _uiState.update { it.copy(gridParams = event.params) }
            is ImageUiEvent.ToggleGridMode -> _uiState.update { it.copy(isGridMode = event.isGrid) }

            // --- 简单的 UI 状态更新 (直接处理) ---
            is ImageUiEvent.UpdateCanvasTransform -> _uiState.update { it.copy(mainScale = event.scale, mainOffset = event.offset) }
            is ImageUiEvent.ChangePanelTab -> _uiState.update { it.copy(rightPanelTabIndex = event.index) }
            is ImageUiEvent.DismissDialogs -> _uiState.update { it.copy(isScreenCropperVisible = false, isMappingDialogVisible = false) }
            is ImageUiEvent.OpenMappingDialog -> openMappingDialog(event.rect)
            is ImageUiEvent.ConfirmMapping -> confirmMapping(event.char)

            // 纯交互，暂不需处理
            is ImageUiEvent.HoverCanvas -> {}
            is ImageUiEvent.ColorPick -> {}
            is ImageUiEvent.UpdateFilterParams -> {
                _uiState.update { it.copy(filterParams = event.params) }
                filterHandler.triggerStepUpdate()
            }
            else -> {}
        }
    }

    // 发送 Toast 消息
    private fun showToast(message: String) {
        viewModelScope.launch {
            _uiEffect.send(message)
        }
    }

    private fun openMappingDialog(rect: Rect) {
        val s = _uiState.value.activeDisplayImage?.bufferedImage ?: return
        _uiState.update { it.copy(isMappingDialogVisible = true, mappingBitmap = ImageUtils.cropImage(s, rect)) }
    }

    private fun confirmMapping(char: String) {
        _uiState.update { it.copy(isMappingDialogVisible = false, mappingBitmap = null) }
        showToast("映射已保存: $char")
    }
}