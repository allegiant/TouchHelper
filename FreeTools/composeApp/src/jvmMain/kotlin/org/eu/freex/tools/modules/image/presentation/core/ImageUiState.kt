package org.eu.freex.tools.modules.image.presentation.core

import org.eu.freex.tools.common.state.LoadingAware
import org.eu.freex.tools.modules.image.domain.model.Pipeline
import org.eu.freex.tools.modules.image.domain.model.Project
import org.eu.freex.tools.modules.image.domain.model.BaseImageEntity
import org.eu.freex.tools.common.state.UiState
import org.eu.freex.tools.modules.image.domain.model.ImageWorkspace
import org.eu.freex.tools.modules.image.domain.model.WorkImage
import java.awt.image.BufferedImage

data class ImageUiState(
    val workspace: ImageWorkspace = ImageWorkspace(),
    val isLoading: Boolean = false,
    val cropperImage: BufferedImage? = null
): UiState<ImageUiState, BaseImageEntity>, LoadingAware<ImageUiState> {

    /**
     * 更新状态组件。
     * 由于 StateComponent 是 sealed interface，编译器会强制检查 exhaustiveness (穷举性)。
     * * 1. 以后如果你新建了 data class Settings : StateComponent
     * 2. 这里的 when 语句会立即报错，提示你缺少 is Settings 分支
     * 3. 这就是最强的编译期强制！
     */
    override fun update(model: BaseImageEntity): ImageUiState {
        val newWorkspace = when (model) {
            is Pipeline -> workspace.copy(pipeline = model)
            is Project -> workspace.copy(project = model)
        }
        return copy(workspace = newWorkspace)
    }

    override fun updateLoading(isLoading: Boolean): ImageUiState {
        return copy(isLoading = isLoading)
    }
}