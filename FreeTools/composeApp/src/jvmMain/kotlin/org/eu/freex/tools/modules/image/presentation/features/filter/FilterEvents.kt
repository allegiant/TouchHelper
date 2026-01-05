package org.eu.freex.tools.modules.image.presentation.features.filter

import kotlinx.coroutines.launch
import org.eu.freex.tools.modules.image.domain.model.EditSession
import org.eu.freex.tools.modules.image.domain.model.ImageFilter
import org.eu.freex.tools.modules.image.domain.model.ViewFilter
import org.eu.freex.tools.modules.image.presentation.core.ImageActionScope
import org.eu.freex.tools.modules.image.presentation.core.ImageUiEvent

// 预览滤镜
data class PreviewFilter(
    val filter: ImageFilter,
    val forceReloadBaseImage: Boolean = false
) : ImageUiEvent {
    override fun ImageActionScope.execute() {
        val pipeline = state.pipeline
        // 调用 Model 方法获取输入图
        val baseImage = if (forceReloadBaseImage || pipeline.draft.baseImage == null) {
            pipeline.getInputImage(pipeline.activeIndex, state.project.activeImage)
        } else {
            pipeline.draft.baseImage
        } ?: return

        scope.launch {
            if (filter is ViewFilter) {
                setPipeline { startEditing(filter, state.project.activeImage) }
                return@launch
            }

            // 调用 UseCase
            pipelineUseCase.processSingle(baseImage, filter)
                .onSuccess { result ->
                    setPipeline {
                        copy(draft = EditSession(activeFilter = filter, previewImage = result, baseImage = baseImage))
                    }
                }
                .onFailure { showToast("预览失败: ${it.message}") }
        }
    }
}

// 更新当前步骤
object UpdateCurrentStep : ImageUiEvent {
    override fun ImageActionScope.execute() {
        val draft = state.pipeline.draft
        val newImage = draft.previewImage

        if (state.pipeline.activeIndex <= 0 || newImage == null) {
            showToast("无法更新：请先选择一个步骤并调整参数")
            return
        }

        launch {
            // UseCase 处理复杂重算
            pipelineUseCase.updateCurrentStep(state.pipeline, newImage)
                .onSuccess { newPipeline ->
                    setPipeline { newPipeline }
                    showToast("步骤已更新")
                }
                .onFailure { showToast("更新失败: ${it.message}") }
        }
    }
}

// 应用新步骤 (追加)
object ApplyNewStep : ImageUiEvent {
    override fun ImageActionScope.execute() {
        val newImage = state.pipeline.draft.previewImage ?: return
        // 简单逻辑直接调 Model
        setPipeline { appendStep(newImage) }
    }
}