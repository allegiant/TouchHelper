package org.eu.freex.tools.modules.image.presentation.contract.events

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.eu.freex.tools.modules.image.domain.model.AppFilter
import org.eu.freex.tools.modules.image.domain.model.BinarizationFilter
import org.eu.freex.tools.modules.image.presentation.contract.ImageActionScope
import org.eu.freex.tools.modules.image.presentation.contract.ImageUiEvent
import org.eu.freex.tools.modules.image.presentation.contract.getPrevStepImage

object ApplyCurrentFilter : ImageUiEvent {
    override fun execute(action: ImageActionScope) {
        val state = action.state
        // 0 是原图，流水线从 1 开始，所以取 index-1
        val source = if (state.selectedPipelineIndex == 0) state.currentSourceImage else state.pipelineSteps.getOrNull(state.selectedPipelineIndex - 1)
        if (source == null) return

        action.launch {
            action.updateState { it.copy(isLoading = true) }
            action.filterProcessor.applyFilter(source, state.currentFilter)
                .onSuccess { newImage ->
                    action.updateState {
                        val newSteps = it.pipelineSteps + newImage
                        it.copy(
                            pipelineSteps = newSteps,
                            selectedPipelineIndex = newSteps.size,
                            isLoading = false
                        )
                    }
                }
        }
    }
}

/**
 * 场景 1: 按钮触发的强制修改 (Modify & Cascade)
 * 对应 ImageUiEvent.ModifyCurrentStep
 * 特点：级联重算后续所有步骤，确保流水线数据一致性
 */
object ModifyCurrentStep : ImageUiEvent {
    override fun execute(action: ImageActionScope) {
        val state = action.state
        // 1. 基础校验
        if (state.selectedPipelineIndex == 0) {
            action.showToast("原图无法修改")
            return
        }
        val stepIndex = state.selectedPipelineIndex - 1

        // 2. 准备输入源 (Input Image)
        val inputImage = action.getPrevStepImage(stepIndex) ?: return action.showToast("无法修改原图")

        // 3. 准备滤镜链条 (Filter Chain)
        // [当前新滤镜] + [后续所有步骤的滤镜]
        val newCurrentFilter = state.currentFilter

        // 从当前步骤的后一步开始，提取后续所有滤镜
        // 注意：这里依赖 WorkImage.appliedFilter 字段来还原滤镜
        val subsequentSteps = state.pipelineSteps.drop(stepIndex + 1)
        val subsequentFilters = subsequentSteps.mapNotNull { it.appliedFilter }

        // 合并成完整的重算任务链
        val filtersToRun = listOf(newCurrentFilter) + subsequentFilters

        // 4. 取消预览，开始正式计算
        action.filterPreviewJob?.cancel()

        action.filterPreviewJob = action.scope.launch {
            action.updateState { it.copy(isLoading = true) }
            // 调用 Processor 批量处理
            action.filterProcessor.processChain(inputImage, filtersToRun)
                .onSuccess { newImages ->
                    // 5. 拼接结果
                    // 保留：被修改步骤之前的所有步骤
                    val keptSteps = state.pipelineSteps.take(stepIndex)

                    // 新流水线 = 保留部分 + 重算部分
                    val finalSteps = keptSteps + newImages

                    action.updateState {
                        it.copy(
                            pipelineSteps = finalSteps,
                            isLoading = false,
                            // 保持选中当前步骤，或者根据需求选中最后一步
                            // selectedPipelineIndex = finalSteps.size
                        )
                    }
                    action.showToast("步骤及其后续流水线已更新")
                }
                .onFailure {
                    it.printStackTrace()
                    action.updateState { s -> s.copy(isLoading = false) }
                    action.showToast("级联更新失败: ${it.message}")
                }
        }
    }
}

data class UpdateFilter(val filter: AppFilter) : ImageUiEvent {
    override fun execute(action: ImageActionScope) {
        // 1. 立即更新 UI 数值
        action.updateState { it.copy(currentFilter = filter) }

        // 2. 只有二值化等交互式滤镜才预览
        if (filter !is BinarizationFilter) return
        val state = action.state
        if (state.selectedPipelineIndex == 0) return

        // 3. 防抖预览
        action.filterPreviewJob?.cancel()
        action.filterPreviewJob = action.scope.launch {
            delay(15)
            val stepIndex = state.selectedPipelineIndex - 1
            val input = action.getPrevStepImage(stepIndex) ?: return@launch

            // 校验：确保当前选中的步骤确实是这个类型的滤镜生成的
            val currentStep = state.pipelineSteps.getOrNull(stepIndex)
            if (currentStep?.appliedFilter != null && currentStep.appliedFilter!!::class != filter::class) return@launch

            // 静默计算
            action.filterProcessor.applyFilter(input, filter).onSuccess { updatedImage ->
                action.updateState { current ->
                    val newSteps = current.pipelineSteps.toMutableList()
                    if (stepIndex in newSteps.indices) {
                        newSteps[stepIndex] = updatedImage
                        current.copy(pipelineSteps = newSteps) // 不改 isLoading
                    } else current
                }
            }
        }
    }
}

data class SelectFilter(val filter: AppFilter) : ImageUiEvent {
    override fun execute(action: ImageActionScope) {
        action.updateState { it.copy(currentFilter = filter) }
    }
}

data class SelectPipelineStep(val index: Int) : ImageUiEvent {
    override fun execute(action: ImageActionScope) {
        action.updateState { it.copy(selectedPipelineIndex = index) }
    }
}

data class DeletePipelineStep(val index: Int) : ImageUiEvent {
    override fun execute(action: ImageActionScope) {
        if (index <= 0) {
            action.showToast("无法删除原图")
            return
        }
        val stepIndexToRemove = index - 1
        val currentSteps = action.state.pipelineSteps
        if (stepIndexToRemove !in currentSteps.indices) return

        action.launch {
            action.updateState { it.copy(isLoading = true) }

            // 1. 拆分列表 (UI 逻辑)
            val keptSteps = currentSteps.take(stepIndexToRemove) // 删除点之前的保留
            val tailSteps = currentSteps.drop(stepIndexToRemove + 1) // 删除点之后的需要重算

            // 2. 提取需要重放的滤镜配方
            val filtersToReplay = tailSteps.mapNotNull { it.appliedFilter }

            // 3. 确定重算的起始基座图片
            val baseImage = keptSteps.lastOrNull()
                ?: action.state.currentSourceImage
                ?: return@launch

            // 4. 调用处理器进行级联重算 (Processor 负责复杂计算)
            filterProcessor.processChain(baseImage, filtersToReplay)
                .onSuccess { recalculatedTail ->
                    // 5. 合并结果并更新
                    action.updateState {  state ->
                        val finalSteps = keptSteps + recalculatedTail
                        state.copy(
                            pipelineSteps = finalSteps,
                            selectedPipelineIndex = finalSteps.size,
                            isLoading = false
                        )
                    }
                }
                .onFailure { e ->
                    e.printStackTrace()
                    action.updateState { it.copy(isLoading = false) }
                    showToast("流水线重算失败: ${e.message}")
                }
        }
    }
}