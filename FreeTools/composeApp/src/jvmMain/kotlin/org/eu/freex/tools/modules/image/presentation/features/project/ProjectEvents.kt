package org.eu.freex.tools.modules.image.presentation.features.project

import org.eu.freex.tools.modules.image.presentation.core.ImageActionScope
import org.eu.freex.tools.modules.image.presentation.core.ImageUiEvent
import java.awt.image.BufferedImage
import java.io.File

// 加载文件
data class LoadFile(val file: File) : ImageUiEvent {
    override fun ImageActionScope.execute() {
        launch {
            projectUseCase.importSourceFile(state.project, file)
                .onSuccess { setProject { it } }
                .onFailure { showToast("导入失败: ${it.message}") }
        }
    }
}

data class SelectSourceImage(val index: Int) : ImageUiEvent {
    override fun ImageActionScope.execute() {
        setProject { selectImage(index) }
    }
}

data class RemoveSourceImage(val index: Int) : ImageUiEvent {
    override fun ImageActionScope.execute() {
        setProject { removeSourceImage(index) }
    }
}

// 保存工程
data class SaveProject(val file: File) : ImageUiEvent {
    override fun ImageActionScope.execute() {
        launch {
            projectUseCase.saveProject(file, state.project, state.pipeline)
                .onSuccess { showToast("保存成功") }
                .onFailure { showToast("保存失败: ${it.message}") }
        }
    }
}

// 加载工程
data class LoadProject(val file: File) : ImageUiEvent {
    override fun ImageActionScope.execute() {
        launch {
            projectUseCase.loadProject(file)
                .onSuccess { (newProject, newPipeline) ->
                    setState { copy(project = newProject, pipeline = newPipeline) }
                    showToast("加载成功")
                }
                .onFailure { showToast("加载失败: ${it.message}") }
        }
    }
}

// 导出图片
data class ExportImage(val file: File) : ImageUiEvent {
    override fun ImageActionScope.execute() {
        val image = state.activeDisplayImage?.bufferedImage ?: return showToast("无图片可导出")
        launch {
            projectUseCase.exportImage(image, file)
                .onSuccess { showToast("导出成功") }
                .onFailure { showToast("导出失败: ${it.message}") }
        }
    }
}

// 截图
object StartScreenCapture : ImageUiEvent {
    override fun ImageActionScope.execute() {
        launch {
            projectUseCase.captureScreen()
                .onSuccess { setScreenCropper(it) }
                .onFailure { showToast("截图失败: ${it.message}") }
        }
    }
}

data class ConfirmScreenCrop(val image: BufferedImage) : ImageUiEvent {
    override fun ImageActionScope.execute() {
        launch {
            projectUseCase.addCapturedImage(state.project, image)
                .onSuccess {
                    setProject { it }
                    setScreenCropper(null)
                }
        }
    }
}