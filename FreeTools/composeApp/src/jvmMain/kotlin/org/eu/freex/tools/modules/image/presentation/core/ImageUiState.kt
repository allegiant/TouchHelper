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
     * 【核心逻辑】获取当前画布应该显示的图片
     * 优先级：滤镜预览图 (Draft) > 流水线当前步骤输出 (Step Output) > 项目原图 (Project Source)
     */
    val activeDisplayImage: WorkImage?
        get() {
            // 1. 优先显示正在调节的预览图
            pipeline.draft.previewImage?.let { return it }

            // 2. 其次显示流水线当前选中的步骤图
            // (注意：这里使用了重构后的 activeOutputImage)
            pipeline.activeOutputImage?.let { return it }

            // 3. 最后显示当前选中的源图
            return project.activeImage
        }

    // 底部历史条显示链
    val displayChain: List<WorkImage>
        get() = buildList {
            project.activeImage?.let { add(it.copy(label = "原图")) }
            addAll(pipeline.steps)
        }
}