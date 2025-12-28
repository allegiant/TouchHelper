package org.eu.freex.tools.modules.image.presentation.viewmodel

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    // 用于控制计算任务的 Job
    private var processJob: Job? = null

    private val repository: ImageRepository = ImageRepositoryImpl(RustDataSource())

    private val _uiState = MutableStateFlow(ImageUiState())
    val uiState = _uiState.asStateFlow()

    fun handleEvent(event: ImageUiEvent) {
        when (event) {
            is ImageUiEvent.LoadFile -> loadFile(event)
            is ImageUiEvent.SelectSourceImage -> selectSource(event.index)
            is ImageUiEvent.RemoveSourceImage -> removeSource(event.index)
            is ImageUiEvent.StartScreenCapture -> startCapture()
            is ImageUiEvent.ConfirmScreenCrop -> saveScreenCapture(event.image)
            is ImageUiEvent.SelectPipelineStep -> _uiState.update { it.copy(selectedPipelineIndex = event.index) }

            // 按钮事件
            is ImageUiEvent.ApplyCurrentFilter -> applyFilterAsNewStep()
            is ImageUiEvent.ModifyCurrentStep -> modifyCurrentStep() // 按钮点击不需要防抖

            is ImageUiEvent.UpdateCanvasTransform -> _uiState.update { it.copy(mainScale = event.scale, mainOffset = event.offset) }
            is ImageUiEvent.ChangePanelTab -> _uiState.update { it.copy(rightPanelTabIndex = event.index) }
            is ImageUiEvent.HoverCanvas -> {}
            is ImageUiEvent.ColorPick -> {}
            is ImageUiEvent.SelectFilter -> _uiState.update { it.copy(currentFilter = event.filter) }

            is ImageUiEvent.UpdateThreshold -> {
                _uiState.update { it.copy(thresholdRange = event.range) }
                triggerStepUpdate() // 滑块拖动：需要微量防抖
            }
            is ImageUiEvent.ToggleRgbAvg -> {
                _uiState.update { it.copy(isRgbAvgEnabled = event.enabled) }
                triggerStepUpdate()
            }

            is ImageUiEvent.UpdateGridParams -> _uiState.update { it.copy(gridParams = event.params) }
            is ImageUiEvent.ToggleGridMode -> _uiState.update { it.copy(isGridMode = event.isGrid) }
            is ImageUiEvent.PerformSegmentation -> performSegmentation()
            is ImageUiEvent.DismissDialogs -> _uiState.update { it.copy(isScreenCropperVisible = false, isMappingDialogVisible = false) }
            is ImageUiEvent.UpdateColorRule -> updateColorRule(event.id) { it.copy(biasHex = event.bias) }
            is ImageUiEvent.ToggleColorRule -> updateColorRule(event.id) { it.copy(isEnabled = event.enabled) }
            is ImageUiEvent.RemoveColorRule -> removeColorRule(event.id)
            is ImageUiEvent.OpenMappingDialog -> openMappingDialog(event.rect)
            is ImageUiEvent.ConfirmMapping -> confirmMapping(event.char)
            else -> {}
        }
    }

    // --- 核心逻辑 ---

    /**
     * 【添加模式】ApplyCurrentFilter
     * 生成新步骤并追加到最后
     */
    private fun applyFilterAsNewStep() {
        val state = _uiState.value
        val source = state.activeDisplayImage ?: return
        val filter = state.currentFilter
        val params = buildFilterParams(state)

        scope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val resultImage = repository.applyFilter(source, filter, params)
                _uiState.update { current ->
                    val newSteps = current.pipelineSteps.toMutableList()
                    newSteps.add(resultImage)
                    current.copy(pipelineSteps = newSteps, selectedPipelineIndex = newSteps.size, isLoading = false)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                e.printStackTrace()
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * 【修改模式】ModifyCurrentStep (按钮触发)
     * 直接执行，无需防抖
     */
    private fun modifyCurrentStep() {
        val state = _uiState.value
        if (state.selectedPipelineIndex == 0) return
        val currentStepIndex = state.selectedPipelineIndex - 1

        processJob?.cancel()
        processJob = scope.launch(Dispatchers.Default) {
            updateSpecificStep(currentStepIndex)
        }
    }

    /**
     * 【实时预览】triggerStepUpdate (滑块触发)
     * 关键修复：加入 15ms 防抖，防止线程积压导致的卡顿
     */
    private fun triggerStepUpdate() {
        val state = _uiState.value
        if (state.currentFilter != ColorFilterType.BINARIZATION) return
        if (state.selectedPipelineIndex == 0) return
        val currentStepIndex = state.selectedPipelineIndex - 1
        val currentStep = state.pipelineSteps.getOrNull(currentStepIndex) ?: return
        if (!currentStep.isBinary && currentStep.label != state.currentFilter.label) return

        processJob?.cancel()
        processJob = scope.launch(Dispatchers.Default) {
            // 【关键】15ms 约等于 60帧/秒。这能合并极高频的事件，
            // 避免 Rust 线程池被瞬间填满，从而消除"卡顿感"，同时保持视觉上的"跟手"。
            delay(15)
            updateSpecificStep(currentStepIndex)
        }
    }

    private suspend fun updateSpecificStep(stepIndex: Int) {
        val state = _uiState.value
        val params = buildFilterParams(state)
        val filter = state.currentFilter
        val inputImage = if (stepIndex == 0) state.currentSourceImage else state.pipelineSteps.getOrNull(stepIndex - 1)
        if (inputImage == null) return

        try {
            val updatedImage = repository.applyFilter(inputImage, filter, params)
            _uiState.update { current ->
                val newSteps = current.pipelineSteps.toMutableList()
                if (stepIndex in newSteps.indices) newSteps[stepIndex] = updatedImage
                current.copy(pipelineSteps = newSteps)
            }
        } catch (e: Exception) {
            // 【关键】忽略 CancellationException，不再打印红色的报错日志
            if (e is CancellationException) throw e
            e.printStackTrace()
        }
    }

    private fun buildFilterParams(state: ImageUiState): Map<String, Any> {
        val params = mutableMapOf<String, Any>()
        if (state.currentFilter == ColorFilterType.BINARIZATION) {
            params["min"] = state.thresholdRange.start.toInt()
            params["max"] = state.thresholdRange.endInclusive.toInt()
            params["rgbAvg"] = state.isRgbAvgEnabled
        }
        return params
    }

    // --- 辅助方法 (保持不变) ---
    private fun loadFile(event: ImageUiEvent.LoadFile) {
        scope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val image = repository.loadFile(event.file)
            _uiState.update {
                if (image != null) {
                    val newList = it.sourceImages + image
                    it.copy(sourceImages = newList, selectedSourceIndex = newList.lastIndex, pipelineSteps = emptyList(), isLoading = false)
                } else { it.copy(isLoading = false) }
            }
        }
    }
    private fun selectSource(index: Int) {
        _uiState.update { it.copy(selectedSourceIndex = index, pipelineSteps = emptyList(), activeRects = emptyList(), segmentationResults = emptyList(), binaryPreview = null) }
    }
    private fun removeSource(index: Int) {
        val currentState = _uiState.value
        val currentList = currentState.sourceImages.toMutableList()
        if (index in currentList.indices) {
            currentList.removeAt(index)
            _uiState.update { state ->
                var newIndex = state.selectedSourceIndex
                if (newIndex == index) newIndex = if (currentList.isNotEmpty()) (index - 1).coerceAtLeast(0) else -1
                else if (newIndex > index) newIndex--
                val reset = (state.selectedSourceIndex == index)
                state.copy(sourceImages = currentList, selectedSourceIndex = newIndex, pipelineSteps = if (reset) emptyList() else state.pipelineSteps, activeRects = if (reset) emptyList() else state.activeRects, segmentationResults = if (reset) emptyList() else state.segmentationResults)
            }
        }
    }
    private fun startCapture() {
        scope.launch(Dispatchers.IO) {
            try { Thread.sleep(300) } catch(e:Exception){}
            try {
                val capture = ImageUtils.captureFullScreen()
                _uiState.update { it.copy(fullScreenCapture = capture, isScreenCropperVisible = true) }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
    private fun saveScreenCapture(image: java.awt.image.BufferedImage) {
        val newWorkImage = WorkImage(bitmap = image.toComposeImageBitmap(), bufferedImage = image, name = "ScreenCapture_${System.currentTimeMillis()}")
        _uiState.update { val newList = it.sourceImages + newWorkImage; it.copy(sourceImages = newList, selectedSourceIndex = newList.lastIndex, isScreenCropperVisible = false) }
    }
    private fun performSegmentation() {
        val state = _uiState.value; val source = state.activeDisplayImage ?: return
        scope.launch { _uiState.update { it.copy(isLoading = true) }; val (rects, subImages) = repository.segmentImage(source, state.isGridMode, state.gridParams, state.activeColorRules); _uiState.update { it.copy(activeRects = rects, segmentationResults = subImages, isLoading = false) } }
    }
    private fun updateColorRule(ruleId: Long, transform: (ColorRule) -> ColorRule) {
        val state = _uiState.value
        if (state.currentScope == RuleScope.GLOBAL) _uiState.update { it.copy(globalColorRules = state.globalColorRules.map { r -> if (r.id == ruleId) transform(r) else r }) }
        else { val idx = state.selectedSourceIndex; val img = state.currentSourceImage ?: return; val newRules = (img.localColorRules ?: state.globalColorRules).map { r -> if (r.id == ruleId) transform(r) else r }; updateSourceImage(idx, img.copy(localColorRules = newRules)) }
    }
    private fun removeColorRule(ruleId: Long) {
        val state = _uiState.value
        if (state.currentScope == RuleScope.GLOBAL) _uiState.update { it.copy(globalColorRules = state.globalColorRules.filterNot { r -> r.id == ruleId }) }
        else { val idx = state.selectedSourceIndex; val img = state.currentSourceImage ?: return; val newRules = (img.localColorRules ?: state.globalColorRules).filterNot { r -> r.id == ruleId }; updateSourceImage(idx, img.copy(localColorRules = newRules)) }
    }
    private fun updateSourceImage(index: Int, newImage: WorkImage) { _uiState.update { s -> val l = s.sourceImages.toMutableList(); if(index in l.indices) l[index] = newImage; s.copy(sourceImages = l) } }
    private fun openMappingDialog(rect: Rect) { val s = _uiState.value.activeDisplayImage?.bufferedImage ?: return; _uiState.update { it.copy(isMappingDialogVisible = true, mappingBitmap = ImageUtils.cropImage(s, rect)) } }
    private fun confirmMapping(char: String) { _uiState.update { it.copy(isMappingDialogVisible = false, mappingBitmap = null) } }
}