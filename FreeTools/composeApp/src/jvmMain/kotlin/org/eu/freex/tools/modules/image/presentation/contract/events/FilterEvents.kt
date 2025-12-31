// 路径: src/jvmMain/kotlin/org/eu/freex/tools/modules/image/presentation/contract/events/FilterEvents.kt
package org.eu.freex.tools.modules.image.presentation.contract.events

import org.eu.freex.tools.modules.image.domain.model.AppFilter
import org.eu.freex.tools.modules.image.presentation.contract.ImageActionScope
import org.eu.freex.tools.modules.image.presentation.contract.ImageUiEvent
import org.eu.freex.tools.modules.image.presentation.contract.getPrevStepImage

// =================================================================================
// 1. 预览与参数调节 (Preview & Adjustment)
// =================================================================================

/**
 * 选中滤镜（菜单点击）
 * 动作：立即基于当前输入跑一遍默认参数，生成预览图放入 currentImage
 */
data class SelectFilter(val filter: AppFilter) : ImageUiEvent {
    override fun ImageActionScope.execute() {
        val inputImage = getPrevStepImage(state.pipeline.selectedPipelineIndex) ?: return

        launch {
            // 单步处理，生成预览
            filterProcessor.processSingle(inputImage, filter)
                .onSuccess { previewImage ->
                    setPipeline { copy(currentImage = previewImage) }
                }
                .onFailure {
                    showToast("滤镜预览失败: ${it.message}")
                }
        }
    }
}

/**
 * 更新滤镜参数（滑块拖动）
 * 动作：增量计算，更新 currentImage
 */
data class UpdateFilter(val filter: AppFilter) : ImageUiEvent {
    override fun ImageActionScope.execute() {
        val inputImage = getPrevStepImage(state.pipeline.selectedPipelineIndex) ?: return

        launch {
            filterProcessor.processSingle(inputImage, filter)
                .onSuccess { previewImage ->
                    setPipeline { copy(currentImage = previewImage) }
                }
        }
    }
}

/**
 * 取消/退出预览
 */
object CancelPreview : ImageUiEvent {
    override fun ImageActionScope.execute() {
        setPipeline { copy(currentImage = null) }
    }
}

// =================================================================================
// 2. 提交与修改 (Commit & Modify)
// =================================================================================

/**
 * 应用当前滤镜
 * 动作：将预览图 (currentImage) 正式加入流水线步骤
 */
object ApplyCurrentFilter : ImageUiEvent {
    override fun ImageActionScope.execute() {
        val preview = state.pipeline.currentImage ?: return

        val insertIndex = state.pipeline.selectedPipelineIndex
        val currentSteps = state.pipeline.pipelineSteps

        // 截断逻辑：保留插入点之前的步骤，追加新步骤
        val newSteps = currentSteps.take(insertIndex).toMutableList()
        newSteps.add(preview)

        setPipeline {
            copy(
                pipelineSteps = newSteps,
                selectedPipelineIndex = newSteps.size,
                currentImage = null // 退出预览
            )
        }
    }
}

/**
 * 修改当前步骤
 * 动作：将当前选中的步骤“加载”进 currentImage，进入预览/编辑模式
 */
object ModifyCurrentStep : ImageUiEvent {
    override fun ImageActionScope.execute() {
        val index = state.pipeline.selectedPipelineIndex
        if (index == 0) {
            showToast("无法修改原图")
            return
        }

        val stepToModify = state.pipeline.pipelineSteps.getOrNull(index - 1) ?: return

        // 进入编辑模式
        setPipeline {
            copy(currentImage = stepToModify)
        }
    }
}

// =================================================================================
// 3. 流水线管理
// =================================================================================

data class SelectPipelineStep(val index: Int) : ImageUiEvent {
    override fun ImageActionScope.execute() {
        setPipeline {
            copy(
                selectedPipelineIndex = index,
                currentImage = null // 切步骤时强制退出预览模式
            )
        }
    }
}

data class DeletePipelineStep(val index: Int) : ImageUiEvent {
    override fun ImageActionScope.execute() {
        if (index <= 0) {
            showToast("无法删除原图")
            return
        }
        val stepIndexToRemove = index - 1
        val currentSteps = state.pipeline.pipelineSteps
        if (stepIndexToRemove !in currentSteps.indices) return

        launch {
            // 1. 拆分
            val keptSteps = currentSteps.take(stepIndexToRemove)
            val tailSteps = currentSteps.drop(stepIndexToRemove + 1)

            // 2. 提取后续滤镜
            val filtersToReplay = tailSteps.mapNotNull { it.appliedFilter }

            // 3. 确定重算基座
            val baseImage = keptSteps.lastOrNull() ?: state.project.currentSourceImage ?: return@launch

            // 4. 级联重算
            filterProcessor.processChain(baseImage, filtersToReplay)
                .onSuccess { recalculatedTail ->
                    setPipeline {
                        val finalSteps = keptSteps + recalculatedTail
                        copy(
                            pipelineSteps = finalSteps,
                            selectedPipelineIndex = finalSteps.size,
                            currentImage = null
                        )
                    }
                }
                .onFailure {
                    showToast("重算流水线失败: ${it.message}")
                }
        }
    }
}