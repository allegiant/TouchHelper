package org.eu.freex.tools.modules.image.presentation.viewmodel

import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope // 必须导入
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eu.freex.tools.model.WorkImage
import org.eu.freex.tools.modules.image.data.repository.ImageRepositoryImpl
import org.eu.freex.tools.modules.image.data.repository.ProjectDatabase
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
            is ImageUiEvent.LoadFile -> {
                viewModelScope.launch {
                    val file = event.file
                    try {
                        val image = ImageUtils.load(file)
                        val workImage = WorkImage(
                            name = file.name,
                            bufferedImage = image,
                            path = file.absolutePath // 【关键】记录路径
                        )
                        resourceHandler.addSourceImage(workImage)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            // 【新增】保存工程
            is ImageUiEvent.SaveProject -> {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        ProjectDatabase.saveProject(
                            event.file,
                            uiState.value.sourceImages,
                            uiState.value.currentFilter,
                            uiState.value.filterParams
                        )
                        // 可选：显示 Toast
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            // 【新增】加载工程
            is ImageUiEvent.LoadProject -> {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val (paths, steps) = ProjectDatabase.loadProject(event.file)

                        // 1. 在 UI 线程清空当前资源
                        withContext(Dispatchers.Main) {
                            // 清空 sourceImages (需要在 ResourceHandler 增加 clear 方法)
                            // resourceHandler.clear()
                        }

                        // 2. 重新加载图片
                        paths.forEach { path ->
                            val f = java.io.File(path)
                            if (f.exists()) {
                                handleEvent(ImageUiEvent.LoadFile(f)) // 复用加载逻辑
                            }
                        }

                        // 3. 恢复滤镜参数 (简略版)
                        if (steps.isNotEmpty()) {
                            // TODO: 解析 steps[0].paramsJson 并设置到 filterParams
                            // 这一步比较复杂，取决于参数如何反序列化，暂时略过
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            // 【新增】导出图片
            is ImageUiEvent.ExportImage -> {
                viewModelScope.launch(Dispatchers.IO) {
                    val bitmap = uiState.value.activeDisplayImage?.bufferedImage
                    if (bitmap != null) {
                        javax.imageio.ImageIO.write(bitmap, "png", event.file)
                    }
                }
            }
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