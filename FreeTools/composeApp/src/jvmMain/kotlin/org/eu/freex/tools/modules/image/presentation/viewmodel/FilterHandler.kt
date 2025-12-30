package org.eu.freex.tools.modules.image.presentation.viewmodel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.eu.freex.tools.model.BinarizationFilter
import org.eu.freex.tools.model.WorkImage
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
     * 基于当前选中的步骤（或原图），应用当前滤镜，追加到流水线末尾
     */
    fun applyFilterAsNewStep() {
        val state = stateFlow.value

        // 【修复 1】不能直接使用 activeDisplayImage (它是 Bitmap)，需要获取 WorkImage 数据模型
        // 根据 selectedPipelineIndex 确定输入源：0 是原图，>0 是流水线中的步骤
        val source: WorkImage = if (state.selectedPipelineIndex == 0) {
            state.currentSourceImage
        } else {
            // UI 索引 1 对应 List 索引 0
            state.pipelineSteps.getOrNull(state.selectedPipelineIndex - 1)
        } ?: return

        val filter = state.currentFilter

        scope.launch(Dispatchers.Default) { // 建议在 Default 线程执行计算
            stateFlow.update { it.copy(isLoading = true) }
            try {
                val resultImage = repository.applyFilter(source, filter)
                stateFlow.update { current ->
                    val newSteps = current.pipelineSteps.toMutableList()
                    newSteps.add(resultImage)
                    current.copy(
                        pipelineSteps = newSteps,
                        selectedPipelineIndex = newSteps.size, // 选中新生成的这步(UI索引 = List大小，因为List从1开始算UI)
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
        // 1. 只有二值化才需要实时预览 (通过类判断)
        if (state.currentFilter !is BinarizationFilter) return
        // 2. 原图不可修改
        if (state.selectedPipelineIndex == 0) return

        // 3. 校验当前步骤是否匹配 (防止在查看"灰度"步骤时拖动"二值化"滑块)
        val currentStepIndex = state.selectedPipelineIndex - 1
        val currentStep = state.pipelineSteps.getOrNull(currentStepIndex) ?: return
        // 检查：如果当前选中步骤的“生成滤镜类型”和“当前UI滤镜类型”不一致，说明用户可能切到了别的图但没切滤镜面板
        // 这里需要 WorkImage 里存了 appliedFilter
        if (currentStep.appliedFilter == null || currentStep.appliedFilter::class != state.currentFilter::class) return

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

        scope.launch(Dispatchers.Default) {
            stateFlow.update { it.copy(isLoading = true) }

            try {
                // 2. 拆分列表
                // 保留的部分：删除点之前的所有步骤
                val keptSteps = currentSteps.take(stepIndexToRemove).toMutableList()

                // 待重算的部分：删除点之后的所有步骤
                val tailSteps = currentSteps.drop(stepIndexToRemove + 1)

                // 3. 确定重算的起始输入源
                var currentInput: WorkImage = keptSteps.lastOrNull()
                    ?: state.currentSourceImage
                    ?: run {
                        stateFlow.update { it.copy(isLoading = false) }
                        return@launch
                    }

                // 4. 级联重算 (Replay)
                for (step in tailSteps) {
                    // 反查滤镜类型
                    val filter = step.appliedFilter
                    if (filter == null) {
                        continue
                    }
                    // 计算
                    val result = repository.applyFilter(currentInput, filter)

                    // 加入新列表，并作为下一步的输入
                    keptSteps.add(result)
                    currentInput = result
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
     * 注意：此方法目前仅更新当前步骤，未触发后续步骤的级联更新(Cascade Update)。
     * 如果修改了中间步骤，后续步骤可能会显示旧数据。如需完美体验需类似 deletePipelineStep 那样重算后续。
     */
    private suspend fun updateSpecificStep(stepIndex: Int) {
        val state = stateFlow.value
        val filter = state.currentFilter

        // 输入源是前一步 (Previous Step)
        val inputImage =
            if (stepIndex == 0) state.currentSourceImage else state.pipelineSteps.getOrNull(
                stepIndex - 1
            )
        if (inputImage == null) return

        try {
            val updatedImage = repository.applyFilter(inputImage, filter)
            stateFlow.update { current ->
                val newSteps = current.pipelineSteps.toMutableList()
                if (stepIndex in newSteps.indices) {
                    newSteps[stepIndex] = updatedImage
                }
                current.copy(pipelineSteps = newSteps)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.printStackTrace()
        }
    }
}