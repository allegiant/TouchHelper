package org.eu.freex.tools.modules.image.presentation.viewmodel

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.eu.freex.tools.model.ColorFilterType
import org.eu.freex.tools.modules.image.domain.repository.ImageRepository
import org.eu.freex.tools.modules.image.presentation.contract.ImageUiState

/**
 * 滤镜处理器：负责滤镜计算、防抖、流水线更新
 */
class FilterHandler(
    private val scope: CoroutineScope,
    private val repository: ImageRepository,
    private val stateFlow: MutableStateFlow<ImageUiState>,
    private val onError: (String) -> Unit // 错误回调
) {
    // 专门处理滤镜计算的防抖 Job
    private var processJob: Job? = null

    /**
     * 【添加模式】ApplyCurrentFilter
     */
    fun applyFilterAsNewStep() {
        val state = stateFlow.value
        val source = state.activeDisplayImage ?: return
        val filter = state.currentFilter
        val params = buildFilterParams(state)

        scope.launch {
            stateFlow.update { it.copy(isLoading = true) }
            try {
                val resultImage = repository.applyFilter(source, filter, params)
                stateFlow.update { current ->
                    val newSteps = current.pipelineSteps.toMutableList()
                    newSteps.add(resultImage)
                    current.copy(
                        pipelineSteps = newSteps,
                        selectedPipelineIndex = newSteps.size,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                e.printStackTrace()
                stateFlow.update { it.copy(isLoading = false) }
                onError("滤镜应用失败: ${e.message}")
            }
        }
    }

    /**
     * 【修改模式】ModifyCurrentStep (按钮触发，无防抖)
     */
    fun modifyCurrentStep() {
        val state = stateFlow.value
        if (state.selectedPipelineIndex == 0) {
            onError("原图无法修改，请使用添加模式")
            return
        }
        val currentStepIndex = state.selectedPipelineIndex - 1

        processJob?.cancel()
        processJob = scope.launch(Dispatchers.Default) {
            updateSpecificStep(currentStepIndex)
        }
    }

    /**
     * 【实时预览】UpdateThreshold / ToggleRgbAvg (滑块触发，带 15ms 防抖)
     */
    fun triggerStepUpdate() {
        val state = stateFlow.value
        // 1. 只有二值化才需要实时预览
        if (state.currentFilter != ColorFilterType.BINARIZATION) return
        // 2. 原图不可修改
        if (state.selectedPipelineIndex == 0) return

        // 3. 校验当前步骤是否匹配
        val currentStepIndex = state.selectedPipelineIndex - 1
        val currentStep = state.pipelineSteps.getOrNull(currentStepIndex) ?: return
        if (!currentStep.isBinary && currentStep.label != state.currentFilter.label) return

        processJob?.cancel()
        processJob = scope.launch(Dispatchers.Default) {
            delay(15) // 15ms 防抖
            updateSpecificStep(currentStepIndex)
        }
    }

    private suspend fun updateSpecificStep(stepIndex: Int) {
        val state = stateFlow.value
        val params = buildFilterParams(state)
        val filter = state.currentFilter

        // 输入源是前一步
        val inputImage = if (stepIndex == 0) state.currentSourceImage else state.pipelineSteps.getOrNull(stepIndex - 1)
        if (inputImage == null) return

        try {
            val updatedImage = repository.applyFilter(inputImage, filter, params)
            stateFlow.update { current ->
                val newSteps = current.pipelineSteps.toMutableList()
                if (stepIndex in newSteps.indices) newSteps[stepIndex] = updatedImage
                current.copy(pipelineSteps = newSteps)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.printStackTrace()
            // 实时预览出错通常不需要频繁弹 Toast，只在日志记录即可
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
}