package org.eu.freex.tools.modules.image.presentation.viewmodel

import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.eu.freex.tools.model.ColorFilterType
import org.eu.freex.tools.model.ColorRule
import org.eu.freex.tools.model.RuleScope
import org.eu.freex.tools.model.WorkImage
import org.eu.freex.tools.modules.image.data.repository.ImageRepositoryImpl
import org.eu.freex.tools.modules.image.data.source.RustDataSource
import org.eu.freex.tools.modules.image.domain.repository.ImageRepository
import org.eu.freex.tools.modules.image.presentation.contract.ImageUiEvent
import org.eu.freex.tools.modules.image.presentation.contract.ImageUiState
import org.eu.freex.tools.utils.ImageUtils

class ImageViewModel : ViewModel() {
    private val scope = CoroutineScope(Dispatchers.Main)

    // 手动依赖注入 (实际项目可用 Koin)
    private val repository: ImageRepository = ImageRepositoryImpl(RustDataSource())

    private val _uiState = MutableStateFlow(ImageUiState())
    val uiState = _uiState.asStateFlow()

    fun handleEvent(event: ImageUiEvent) {
        when (event) {
            is ImageUiEvent.LoadFile -> loadFile(event)
            is ImageUiEvent.SelectSourceImage -> selectSource(event.index)
            is ImageUiEvent.RemoveSourceImage -> removeSource(event.index)
            is ImageUiEvent.ApplyCurrentFilter -> applyFilter()
            is ImageUiEvent.PerformSegmentation -> performSegmentation()

            // 简单的状态更新直接处理
            is ImageUiEvent.UpdateCanvasTransform -> _uiState.update {
                it.copy(
                    mainScale = event.scale,
                    mainOffset = event.offset
                )
            }

            is ImageUiEvent.ChangePanelTab -> _uiState.update { it.copy(rightPanelTabIndex = event.index) }
            // 1. 修改阈值更新事件：不仅更新数值，还要触发预览
            is ImageUiEvent.UpdateThreshold -> {
                _uiState.update { it.copy(thresholdRange = event.range) }
                triggerPreview()
            }

            // 2. 修改 RGB 平均值切换事件：同样触发预览
            is ImageUiEvent.ToggleRgbAvg -> {
                _uiState.update { it.copy(isRgbAvgEnabled = event.enabled) }
                triggerPreview()
            }

            // 3. 切换滤镜时，如果是二值化，也触发一次预览；如果是其他，清除预览
            is ImageUiEvent.SelectFilter -> {
                _uiState.update {
                    it.copy(currentFilter = event.filter, binaryPreview = null)
                }
                if (event.filter == ColorFilterType.BINARIZATION) {
                    triggerPreview()
                }
            }
            is ImageUiEvent.UpdateGridParams -> _uiState.update { it.copy(gridParams = event.params) }
            is ImageUiEvent.ToggleGridMode -> _uiState.update { it.copy(isGridMode = event.isGrid) }

            // 弹窗
            is ImageUiEvent.StartScreenCapture -> startCapture()
            is ImageUiEvent.ConfirmScreenCrop -> {
                // 添加截图到资源列表
                // ... 省略具体实现，类似于 loadFile
                _uiState.update { it.copy(isScreenCropperVisible = false) }
            }

            is ImageUiEvent.DismissDialogs -> _uiState.update {
                it.copy(isScreenCropperVisible = false, isMappingDialogVisible = false)
            }
            // --- 规则管理 ---
            is ImageUiEvent.UpdateColorRule -> updateColorRule(event.id) { it.copy(biasHex = event.bias) }
            is ImageUiEvent.ToggleColorRule -> updateColorRule(event.id) { it.copy(isEnabled = event.enabled) }
            is ImageUiEvent.RemoveColorRule -> removeColorRule(event.id)

            // --- 字库映射 ---
            is ImageUiEvent.OpenMappingDialog -> openMappingDialog(event.rect)
            is ImageUiEvent.ConfirmMapping -> confirmMapping(event.char)
            else -> {}
        }
    }

    private fun loadFile(event: ImageUiEvent.LoadFile) {
        scope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val image = repository.loadFile(event.file)
            _uiState.update {
                if (image != null) {
                    val newList = it.sourceImages + image
                    it.copy(
                        sourceImages = newList,
                        selectedSourceIndex = newList.lastIndex,
                        pipelineSteps = emptyList(), // 重置流水线
                        isLoading = false
                    )
                } else {
                    it.copy(isLoading = false)
                }
            }
        }
    }

    private fun applyFilter() {
        val state = _uiState.value
        val source = state.activeDisplayImage ?: return
        val filter = state.currentFilter

        // 构造参数
        val params = mutableMapOf<String, Any>()
        if (filter == ColorFilterType.BINARIZATION) {
            params["min"] = state.thresholdRange.start.toInt()
            params["max"] = state.thresholdRange.endInclusive.toInt()
            params["rgbAvg"] = state.isRgbAvgEnabled
        }

        scope.launch {
            _uiState.update { it.copy(isLoading = true) }
            // 调用 Domain 层
            val result = repository.applyFilter(source, filter, params)

            _uiState.update {
                val newSteps = it.pipelineSteps + result
                it.copy(
                    pipelineSteps = newSteps,
                    selectedPipelineIndex = newSteps.size, // 选中最新的一步
                    isLoading = false
                )
            }
        }
    }

    private fun performSegmentation() {
        val state = _uiState.value
        val source = state.activeDisplayImage ?: return

        scope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val (rects, subImages) = repository.segmentImage(
                source, state.isGridMode, state.gridParams, state.activeColorRules
            )
            _uiState.update {
                it.copy(
                    activeRects = rects,
                    segmentationResults = subImages,
                    isLoading = false
                )
            }
        }
    }

    private fun selectSource(index: Int) {
        _uiState.update {
            it.copy(
                selectedSourceIndex = index,
                pipelineSteps = emptyList(),
                activeRects = emptyList(),
                segmentationResults = emptyList()
            )
        }
    }

    private fun removeSource(index: Int) {
        val currentState = _uiState.value
        val currentList = currentState.sourceImages.toMutableList()

        if (index in currentList.indices) {
            currentList.removeAt(index)

            _uiState.update { state ->
                // 计算新的选中索引
                var newIndex = state.selectedSourceIndex
                if (newIndex == index) {
                    // 如果删除了当前选中的，选中前一个，或者空
                    newIndex = if (currentList.isNotEmpty()) {
                        (index - 1).coerceAtLeast(0)
                    } else {
                        -1
                    }
                } else if (newIndex > index) {
                    // 如果删除了前面的，当前索引减1
                    newIndex--
                }

                state.copy(
                    sourceImages = currentList,
                    selectedSourceIndex = newIndex,
                    // 如果删除了当前正在查看的图片，重置流水线
                    pipelineSteps = if (state.selectedSourceIndex == index) emptyList() else state.pipelineSteps,
                    activeRects = if (state.selectedSourceIndex == index) emptyList() else state.activeRects,
                    segmentationResults = if (state.selectedSourceIndex == index) emptyList() else state.segmentationResults
                )
            }
        }
    }

    private fun startCapture() {
        scope.launch(Dispatchers.IO) {
            // 延迟一点以防截到窗口本身
            delay(300)
            val capture = ImageUtils.captureFullScreen() // 假设您有这个工具方法
            // 回到主线程更新 UI
            // _uiState.update ...
        }
    }

    /**
     * 更新颜色规则 (通用方法)
     * 根据当前 Scope (全局/局部) 查找并更新规则
     */
    private fun updateColorRule(ruleId: Long, transform: (ColorRule) -> ColorRule) {
        val state = _uiState.value

        if (state.currentScope == RuleScope.GLOBAL) {
            // 更新全局规则
            val newRules = state.globalColorRules.map {
                if (it.id == ruleId) transform(it) else it
            }
            _uiState.update { it.copy(globalColorRules = newRules) }
        } else {
            // 更新当前图片的局部规则
            val currentIdx = state.selectedSourceIndex
            val currentImg = state.currentSourceImage ?: return

            // 复制旧规则列表 (如果为空则从全局复制一份作为起点，或者视业务需求而定)
            val oldRules = currentImg.localColorRules ?: state.globalColorRules
            val newRules = oldRules.map {
                if (it.id == ruleId) transform(it) else it
            }

            // 更新图片对象
            val newImg = currentImg.copy(localColorRules = newRules)
            updateSourceImage(currentIdx, newImg)
        }
    }

    private fun removeColorRule(ruleId: Long) {
        val state = _uiState.value

        if (state.currentScope == RuleScope.GLOBAL) {
            val newRules = state.globalColorRules.filterNot { it.id == ruleId }
            _uiState.update { it.copy(globalColorRules = newRules) }
        } else {
            val currentIdx = state.selectedSourceIndex
            val currentImg = state.currentSourceImage ?: return
            val oldRules = currentImg.localColorRules ?: state.globalColorRules
            val newRules = oldRules.filterNot { it.id == ruleId }

            val newImg = currentImg.copy(localColorRules = newRules)
            updateSourceImage(currentIdx, newImg)
        }
    }

    private fun openMappingDialog(rect: Rect) {
        // 从当前显示的图中裁剪出字符图片
        val source = _uiState.value.activeDisplayImage?.bufferedImage ?: return
        val cropped = ImageUtils.cropImage(source, rect)

        _uiState.update {
            it.copy(
                isMappingDialogVisible = true,
                mappingBitmap = cropped
            )
        }
    }

    private fun confirmMapping(char: String) {
        val bitmap = _uiState.value.mappingBitmap
        if (bitmap != null) {
            // TODO: 这里调用 Repository 保存字库 (例如 FontRepository.add(char, bitmap))
            println("Mapping confirmed: $char")
        }

        // 关闭弹窗
        _uiState.update {
            it.copy(
                isMappingDialogVisible = false,
                mappingBitmap = null
            )
        }
    }

    /**
     * 辅助方法：更新 sourceImages 列表中的特定项
     */
    private fun updateSourceImage(index: Int, newImage: WorkImage) {
        _uiState.update { state ->
            val newList = state.sourceImages.toMutableList()
            if (index in newList.indices) {
                newList[index] = newImage
            }
            state.copy(sourceImages = newList)
        }
    }

    // --- 新增：触发预览逻辑 ---
    private fun triggerPreview() {
        val state = _uiState.value
        // 只有当前是二值化滤镜，且有图可处理时才生成预览
        if (state.currentFilter != ColorFilterType.BINARIZATION) return
        val source = state.activeDisplayImage ?: return

        // 防抖动处理在实际开发中很有必要，这里先展示核心逻辑
        scope.launch(Dispatchers.Default) {
            val params = mapOf(
                "min" to state.thresholdRange.start.toInt(),
                "max" to state.thresholdRange.endInclusive.toInt(),
                "rgbAvg" to state.isRgbAvgEnabled
            )

            // 调用 Repository 生成预览图，但不加入流水线 (Pipeline)
            // 注意：applyFilter 可能会比较耗时，频繁调用建议加 Debounce
            val previewImage = repository.applyFilter(source, state.currentFilter, params)

            // 更新 UI 状态中的 binaryPreview
            _uiState.update {
                it.copy(binaryPreview = previewImage)
            }
        }
    }
}