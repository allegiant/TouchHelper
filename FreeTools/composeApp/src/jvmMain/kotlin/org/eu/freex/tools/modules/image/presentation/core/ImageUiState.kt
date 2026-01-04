package org.eu.freex.tools.modules.image.presentation.core

import org.eu.freex.tools.modules.image.domain.model.WorkImage
import org.eu.freex.tools.modules.image.presentation.features.pipeline.PipelineState
import org.eu.freex.tools.modules.image.presentation.features.project.ProjectState
import org.eu.freex.tools.modules.image.presentation.features.tools.UiInteractionState


// --- 总 UI 状态 ---
data class ImageUiState(
    val project: ProjectState = ProjectState(),
    val pipeline: PipelineState = PipelineState(),
    val ui: UiInteractionState = UiInteractionState(),
) {
    /**
     * 画布显示逻辑：
     * 1. 如果有草稿预览图 (Draft Preview)，优先显示草稿（实时反馈调节结果）
     * 2. 否则显示当前选中的步骤
     * 3. 最后兜底原图
     */
    val activeDisplayImage: WorkImage?
        get() = when {
            pipeline.draft.previewImage != null -> pipeline.draft.previewImage
            pipeline.selectedPipelineIndex > 0 -> pipeline.pipelineSteps.getOrNull(pipeline.selectedPipelineIndex - 1)
            else -> project.currentSourceImage?.copy(label = "原图")
        }

    // 底部历史条显示链
    val displayChain: List<WorkImage>
        get() = buildList {
            project.currentSourceImage?.let { add(it.copy(label = "原图")) }
            addAll(pipeline.pipelineSteps)
        }
}

// 辅助扩展方法：获取当前选中步骤的前一步图像（作为下一次处理的输入）
fun ImageUiState.getPreviousImageForProcessing(): WorkImage? {
    val index = pipeline.selectedPipelineIndex
    return if (index == 0) {
        project.currentSourceImage
    } else {
        pipeline.pipelineSteps.getOrNull(index - 1)
    }
}