package org.eu.freex.tools.modules.image.presentation.features.pipeline


import org.eu.freex.tools.modules.image.domain.model.EditSession
import org.eu.freex.tools.modules.image.domain.model.ViewFilter
import org.eu.freex.tools.modules.image.presentation.core.ImageActionScope
import org.eu.freex.tools.modules.image.presentation.core.ImageUiEvent
import org.eu.freex.tools.modules.image.presentation.core.getPrevStepImage
import kotlin.math.max

// =================================================================================
// 3. 导航与管理
// =================================================================================

/**
 * 选中流水线中的某个步骤
 * 动作：除了移动指针，还需要把该步骤的 Filter 加载到 Draft 中，以便 Inspector 回显
 */
data class SelectPipelineStep(val index: Int) : ImageUiEvent {
    override fun ImageActionScope.execute() {
        val targetFilter = if (index == 0) {
            ViewFilter
        } else {
            state.pipeline.steps.getOrNull(index - 1)?.appliedFilter ?: ViewFilter
        }

        // 【关键修复】
        // 切换步骤时，立即准备好该步骤的“输入图”作为 baseImage。
        // 这样当用户拖动滑块时，是基于“输入图”进行计算，而不是基于“当前结果”计算。
        val inputIndex = max(0, index - 1)
        val baseImage = getPrevStepImage(inputIndex)

        setPipeline {
            copy(
                activeIndex = index,
                draft = EditSession(
                    activeFilter = targetFilter,
                    previewImage = null,
                    baseImage = baseImage // 预加载 BaseImage
                )
            )
        }
    }
}

data class DeletePipelineStep(val index: Int) : ImageUiEvent {
    override fun ImageActionScope.execute() {
        if (index <= 0) return
        val stepIndexToRemove = index - 1
        val currentSteps = state.pipeline.steps

        launch {
            val keptSteps = currentSteps.take(stepIndexToRemove)
            val tailSteps = currentSteps.drop(stepIndexToRemove + 1)
            val filtersToReplay = tailSteps.mapNotNull { it.appliedFilter }

            val baseImage = if (keptSteps.isNotEmpty()) keptSteps.last() else state.project.activeImage
            if (baseImage == null) return@launch

            val recalculatedTail = filterService.processChain(baseImage, filtersToReplay).getOrElse { emptyList() }

            val finalSteps = keptSteps + recalculatedTail

            setPipeline {
                copy(
                    steps = finalSteps,
                    activeIndex = finalSteps.size,
                    draft = EditSession()
                )
            }
        }
    }
}
