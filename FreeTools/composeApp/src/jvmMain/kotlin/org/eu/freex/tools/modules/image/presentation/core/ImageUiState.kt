package org.eu.freex.tools.modules.image.presentation.core

import org.eu.freex.tools.modules.image.domain.model.Pipeline
import org.eu.freex.tools.modules.image.domain.model.Project
import org.eu.freex.tools.modules.image.domain.model.WorkImage
import java.awt.image.BufferedImage


// --- 总 UI 状态 ---
data class ImageUiState(
    val project: Project = Project(),
    val pipeline: Pipeline = Pipeline(),
    val isLoading: Boolean = false, // Loading 比较特殊，通常独立于内容
    val cropperImage: BufferedImage? = null
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
            pipeline.activeImage != null -> pipeline.activeImage
            else -> project.activeImage?.copy(label = "原图")
        }

    // 底部历史条显示链
    val displayChain: List<WorkImage>
        get() = buildList {
            project.activeImage?.let { add(it.copy(label = "原图")) }
            addAll(pipeline.steps)
        }
}