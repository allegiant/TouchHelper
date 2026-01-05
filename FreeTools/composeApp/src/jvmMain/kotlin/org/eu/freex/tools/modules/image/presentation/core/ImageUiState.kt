package org.eu.freex.tools.modules.image.presentation.core

import org.eu.freex.tools.modules.image.domain.model.Pipeline
import org.eu.freex.tools.modules.image.domain.model.Project
import org.eu.freex.tools.modules.image.domain.model.WorkImage
import java.awt.image.BufferedImage

data class ImageUiState(
    val project: Project = Project(),
    val pipeline: Pipeline = Pipeline(),
    val isLoading: Boolean = false,
    val cropperImage: BufferedImage? = null
) {
    /**
     * DSL 辅助方法：更新 Pipeline
     * 允许 Handler 写法：state.mapPipeline { updateDraft(...) }
     */
    fun mapPipeline(transformer: Pipeline.() -> Pipeline): ImageUiState {
        return copy(pipeline = pipeline.transformer())
    }

    /**
     * DSL 辅助方法：更新 Project
     * 允许 Handler 写法：state.mapProject { selectImage(...) }
     */
    fun mapProject(transformer: Project.() -> Project): ImageUiState {
        return copy(project = project.transformer())
    }

    // 画布显示逻辑
    val activeDisplayImage: WorkImage?
        get() {
            // 1. 优先显示草稿
            pipeline.draft.previewImage?.let { return it }
            // 2. 其次显示流水线输出
            pipeline.activeOutputImage?.let { return it }
            // 3. 最后显示原图
            return project.activeImage?.copy(label = "原图")
        }

    val displayChain: List<WorkImage>
        get() = buildList {
            project.activeImage?.let { add(it.copy(label = "原图")) }
            addAll(pipeline.steps)
        }
}