package org.eu.freex.tools.modules.image.presentation.features.project

import org.eu.freex.tools.modules.image.application.ProjectUseCase
import org.eu.freex.tools.modules.image.presentation.core.ImageEventHandler
import org.eu.freex.tools.modules.image.presentation.core.ImageUiEvent
import org.eu.freex.tools.modules.image.presentation.core.ImageUiState
import org.eu.freex.tools.modules.image.presentation.core.ProjectEvent
import org.eu.freex.tools.modules.image.presentation.core.commitTo

class ProjectEventHandler(
    private val projectUseCase: ProjectUseCase
) : ImageEventHandler {

    override suspend fun handle(
        event: ImageUiEvent,
        state: ImageUiState,
        showToast: (String) -> Unit
    ): ImageUiState? {
        if (event !is ProjectEvent) return null
        val project = state.project
        return when (event) {
            is LoadFile -> {
                projectUseCase.importSourceFile(state.project, event.file)
                    .map { state.copy(project = it) }
                    .getOrElse {
                        showToast("导入失败: ${it.message}")
                        state
                    }
            }
            is SelectSourceImage -> project.selectImage(event.index) commitTo state
            is RemoveSourceImage -> project.removeSourceImage(event.index) commitTo state
            is SaveProject -> {
                projectUseCase.saveProject(event.file, state.project, state.pipeline)
                    .onSuccess { showToast("保存成功") }
                    .onFailure { showToast("保存失败: ${it.message}") }
                state
            }
            is LoadProject -> {
                projectUseCase.loadProject(event.file)
                    .map { (newProject, newPipeline) ->
                        newProject commitTo state
                        newPipeline commitTo state
                    }
                    .onSuccess { showToast("加载成功") }
                    .onFailure { showToast("加载失败: ${it.message}") }
                    .getOrElse { state }
            }
            is ExportImage -> {
                val image = state.activeDisplayImage?.bufferedImage
                if (image == null) {
                    showToast("无图片")
                    state
                } else {
                    projectUseCase.exportImage(image, event.file)
                        .onSuccess { showToast("导出成功") }
                        .onFailure { showToast("导出失败: ${it.message}") }
                    state
                }
            }
            is StartScreenCapture -> {
                projectUseCase.captureScreen()
                    .map { state.copy(cropperImage = it) }
                    .onFailure { showToast("截图失败") }
                    .getOrElse { state }
            }
            is ConfirmScreenCrop -> {
                projectUseCase.addCapturedImage(state.project, event.image)
                    .map { state.copy(project = it, cropperImage = null) }
                    .getOrElse { state }
            }
            else -> state
        }
    }
}