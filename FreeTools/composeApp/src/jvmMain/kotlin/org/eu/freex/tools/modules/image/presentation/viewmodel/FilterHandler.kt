package org.eu.freex.tools.modules.image.presentation.viewmodel

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.eu.freex.tools.model.*
import org.eu.freex.tools.modules.image.domain.repository.ImageRepository
import org.eu.freex.tools.modules.image.presentation.contract.ImageUiState

/**
 * 滤镜处理器：负责滤镜计算、防抖、流水线更新（增、删、改）
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
     * 基于当前画布显示的图像，应用当前滤镜，追加到流水线末尾
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
                        selectedPipelineIndex = newSteps.size, // 选中新生成的这步
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
     * 【修改模式】ModifyCurrentStep (按钮触发)
     * 强制刷新当前选中的步骤，无防抖
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
     * 【实时预览】UpdateThreshold / ToggleRgbAvg (滑块触发)
     * 带有 15ms 防抖，用于二值化等高频操作
     */
    fun triggerStepUpdate() {
        val state = stateFlow.value
        // 1. 只有二值化才需要实时预览
        if (state.currentFilter != ColorFilterType.BINARIZATION) return
        // 2. 原图不可修改
        if (state.selectedPipelineIndex == 0) return

        // 3. 校验当前步骤是否匹配 (防止在查看"灰度"步骤时拖动"二值化"滑块)
        val currentStepIndex = state.selectedPipelineIndex - 1
        val currentStep = state.pipelineSteps.getOrNull(currentStepIndex) ?: return
        if (!currentStep.isBinary && currentStep.label != state.currentFilter.label) return

        processJob?.cancel()
        processJob = scope.launch(Dispatchers.Default) {
            delay(15) // 15ms 防抖，保证跟手且不卡顿
            updateSpecificStep(currentStepIndex)
        }
    }

    /**
     * 【删除步骤】DeletePipelineStep
     * 删除指定步骤，并基于新的依赖链重新计算后续所有步骤 (Cascade Replay)
     * @param uiIndex UI上的索引 (0 是原图，1 是第1步...)
     */
    fun deletePipelineStep(uiIndex: Int) {
        // 1. 校验：原图 (index 0) 不可由此删除
        if (uiIndex <= 0) {
            onError("无法删除原图")
            return
        }

        val stepIndexToRemove = uiIndex - 1 // 转换为 pipelineSteps 列表下标
        val state = stateFlow.value
        val currentSteps = state.pipelineSteps

        if (stepIndexToRemove !in currentSteps.indices) return

        scope.launch {
            stateFlow.update { it.copy(isLoading = true) }

            try {
                // 2. 拆分列表
                // 保留的部分：删除点之前的所有步骤
                val keptSteps = currentSteps.take(stepIndexToRemove).toMutableList()

                // 待重算的部分：删除点之后的所有步骤
                val tailSteps = currentSteps.drop(stepIndexToRemove + 1)

                // 3. 确定重算的起始输入源
                // 【关键修复】使用 Elvis 操作符确保类型为 WorkImage (非空)，避免 var 的 Smart Cast 问题
                var currentInput: WorkImage = keptSteps.lastOrNull()
                    ?: state.currentSourceImage
                    ?: run {
                        // 极端情况：没有任何图片，直接返回
                        stateFlow.update { it.copy(isLoading = false) }
                        return@launch
                    }

                // 4. 级联重算 (Replay)
                for (step in tailSteps) {
                    // 反查滤镜类型
                    val filter = findFilterByLabel(step.label)
                    if (filter == null) {
                        continue
                    }
                    val params = step.params ?: emptyMap()

                    // 计算
                    val result = repository.applyFilter(currentInput, filter, params)

                    // 加入新列表，并作为下一步的输入
                    keptSteps.add(result)
                    currentInput = result // 此时类型匹配 (WorkImage)
                }

                // 5. 更新状态
                stateFlow.update {
                    it.copy(
                        pipelineSteps = keptSteps,
                        // 自动选中最后一步
                        selectedPipelineIndex = keptSteps.size,
                        isLoading = false
                    )
                }

            } catch (e: Exception) {
                if (e is CancellationException) throw e
                e.printStackTrace()
                stateFlow.update { it.copy(isLoading = false) }
                onError("重算流水线失败: ${e.message}")
            }
        }
    }

    // --- 内部私有方法 ---

    /**
     * 重新计算并更新指定下标的步骤
     */
    private suspend fun updateSpecificStep(stepIndex: Int) {
        val state = stateFlow.value
        val params = buildFilterParams(state)
        val filter = state.currentFilter

        // 输入源是前一步 (Previous Step)
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
            // 忽略任务取消异常
            if (e is CancellationException) throw e
            e.printStackTrace()
        }
    }

    /**
     * 构建滤镜参数 Map
     */
    private fun buildFilterParams(state: ImageUiState): Map<String, Any> {
        val params = mutableMapOf<String, Any>()
        if (state.currentFilter == ColorFilterType.BINARIZATION) {
            params["min"] = state.thresholdRange.start.toInt()
            params["max"] = state.thresholdRange.endInclusive.toInt()
            params["rgbAvg"] = state.isRgbAvgEnabled
        }
        return params
    }

    /**
     * 根据 Label 反查 ImageFilter 枚举 (用于流水线重算)
     */
    private fun findFilterByLabel(label: String): ImageFilter? {
        return ColorFilterType.entries.find { it.label == label }
            ?: BlackWhiteFilterType.entries.find { it.label == label }
            ?: CommonFilterType.entries.find { it.label == label }
    }
}