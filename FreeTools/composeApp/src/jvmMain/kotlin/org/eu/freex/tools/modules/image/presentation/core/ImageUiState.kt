package org.eu.freex.tools.modules.image.presentation.core

import org.eu.freex.tools.modules.image.domain.model.Pipeline
import org.eu.freex.tools.modules.image.domain.model.Project
import org.eu.freex.tools.modules.image.domain.model.ImageEntity
import org.eu.freex.tools.common.state.UiState
import org.eu.freex.tools.modules.image.domain.model.WorkImage
import java.awt.image.BufferedImage

data class ImageUiState(
    val project: Project = Project(),
    val pipeline: Pipeline = Pipeline(),
    val isLoading: Boolean = false,
    val cropperImage: BufferedImage? = null
): UiState<ImageUiState, ImageEntity> {

    /**
     * 更新状态组件。
     * 由于 StateComponent 是 sealed interface，编译器会强制检查 exhaustiveness (穷举性)。
     * * 1. 以后如果你新建了 data class Settings : StateComponent
     * 2. 这里的 when 语句会立即报错，提示你缺少 is Settings 分支
     * 3. 这就是最强的编译期强制！
     */
    override fun update(model: ImageEntity): ImageUiState {
        return when (model) {
            is Pipeline -> copy(pipeline = model)
            is Project -> copy(project = model)
        }
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