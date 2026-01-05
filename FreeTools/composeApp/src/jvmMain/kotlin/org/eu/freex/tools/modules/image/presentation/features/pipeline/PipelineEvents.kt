package org.eu.freex.tools.modules.image.presentation.features.pipeline

import org.eu.freex.tools.modules.image.domain.model.ViewFilter
import org.eu.freex.tools.modules.image.presentation.core.ImageActionScope
import org.eu.freex.tools.modules.image.presentation.core.ImageUiEvent

// =================================================================================
// 3. 导航与管理
// =================================================================================

/**
 * 选中流水线中的某个步骤
 * 动作：移动指针，并进入"编辑模式"（加载该步骤的参数到面板）
 */
data class SelectPipelineStep(val index: Int) : ImageUiEvent {
    override fun ImageActionScope.execute() {
        // 1. 获取目标步骤的滤镜（如果是第0步，则是 ViewFilter/无滤镜）
        val targetFilter = if (index == 0) {
            ViewFilter
        } else {
            state.pipeline.steps.getOrNull(index - 1)?.appliedFilter ?: ViewFilter
        }

        // 2. 更新 Pipeline 状态
        setPipeline {
            // copy(activeIndex = index) 先移动指针
            // startEditing 会自动根据新的 index 找到"上一步"的图片作为 BaseImage
            copy(activeIndex = index)
                .startEditing(targetFilter, state.project.activeImage)
        }
    }
}

/**
 * 删除步骤
 * 动作：调用 UseCase 删除指定步骤并重算后续
 */
data class DeletePipelineStep(val index: Int) : ImageUiEvent {
    override fun ImageActionScope.execute() {
        if (index <= 0) {
            showToast("无法删除原图")
            return
        }

        launch {
            // 直接调用 UseCase，无需自己在 UI 层处理 List 拼接和重算
            pipelineUseCase.deleteStep(state.pipeline, state.project, index)
                .onSuccess { newPipeline ->
                    setPipeline { newPipeline }
                }
                .onFailure {
                    showToast("删除失败: ${it.message}")
                }
        }
    }
}