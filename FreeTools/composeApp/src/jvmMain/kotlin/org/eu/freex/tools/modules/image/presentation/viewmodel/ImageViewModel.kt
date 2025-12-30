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
    private val segmentationHandler =
        SegmentationHandler(viewModelScope, repository, _uiState, ::showToast)

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
                        val filtersToSave = uiState.value.pipelineSteps.mapNotNull { it.appliedFilter }
                        ProjectDatabase.saveProject(
                            event.file,
                            uiState.value.sourceImages,
                            currentFilters = filtersToSave
                        )
                        showToast("工程已保存")
                    } catch (e: Exception) {
                        e.printStackTrace()
                        showToast("保存失败")
                    }
                }
            }

            // 【新增】加载工程
            is ImageUiEvent.LoadProject -> {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        // 假设 ProjectDatabase.loadProject 现在返回 Pair<List<File>, List<WorkImage>>
                        // 或者 Pair<List<String>, List<AppFilter>>，具体取决于你的 DB 层实现。
                        // 这里假设它返回的是 (图片路径列表, 还原后的步骤列表)
                        val (paths, loadedSteps) = ProjectDatabase.loadProject(event.file)

                        withContext(Dispatchers.Main) {
                            val files = paths.mapNotNull {
                                val file = java.io.File(it)
                                if (file.exists()) file else null
                            }

                            // 2. 恢复源图片
                            files.forEach { file -> ImageUiEvent.LoadFile(file) }

                            // 3. 【关键修复】恢复流水线步骤
                            val firstImage = files.firstOrNull() ?: return@withContext

                            val workImages = loadedSteps.map {
                                WorkImage(
                                    name = it.name,
                                    bufferedImage = ImageUtils.load(file = firstImage),
                                    label = it.name,
                                    appliedFilter = it
                                )
                            }
                            _uiState.update {
                                it.copy(
                                    pipelineSteps = workImages, // 直接赋值
                                    selectedPipelineIndex = loadedSteps.size // 选中最后一步
                                )
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        showToast("加载工程失败: ${e.message}")
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
            is ImageUiEvent.SelectFilter -> _uiState.update { it.copy(currentFilter = event.filter) }
            is ImageUiEvent.UpdateFilter -> {
                // 1. 更新 UI 状态中的 currentFilter (让滑块数值变化生效)
                _uiState.update { it.copy(currentFilter = event.filter) }

                // 2. 触发 FilterHandler 的防抖预览逻辑
                filterHandler.triggerStepUpdate()
            }
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
            is ImageUiEvent.UpdateCanvasTransform -> _uiState.update {
                it.copy(
                    mainScale = event.scale,
                    mainOffset = event.offset
                )
            }

            is ImageUiEvent.ChangePanelTab -> _uiState.update { it.copy(rightPanelTabIndex = event.index) }
            is ImageUiEvent.DismissDialogs -> _uiState.update {
                it.copy(
                    isScreenCropperVisible = false,
                    isMappingDialogVisible = false
                )
            }

            is ImageUiEvent.OpenMappingDialog -> openMappingDialog(event.rect)
            is ImageUiEvent.ConfirmMapping -> confirmMapping(event.char)

            // 纯交互，暂不需处理
            is ImageUiEvent.HoverCanvas -> {}
            is ImageUiEvent.ColorPick -> {}
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
        _uiState.update {
            it.copy(
                isMappingDialogVisible = true,
                mappingBitmap = ImageUtils.cropImage(s, rect)
            )
        }
    }

    private fun confirmMapping(char: String) {
        _uiState.update { it.copy(isMappingDialogVisible = false, mappingBitmap = null) }
        showToast("映射已保存: $char")
    }
}