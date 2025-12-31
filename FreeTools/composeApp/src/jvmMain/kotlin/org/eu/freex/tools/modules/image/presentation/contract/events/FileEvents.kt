package org.eu.freex.tools.modules.image.presentation.contract.events

import org.eu.freex.tools.modules.image.domain.model.WorkImage
import org.eu.freex.tools.modules.image.presentation.contract.ImageActionScope
import org.eu.freex.tools.modules.image.presentation.contract.ImageUiEvent
import java.io.File
import javax.imageio.ImageIO

class LoadFile(val file: File) : ImageUiEvent {

    override fun ImageActionScope.execute() {
        launch {
            resourceProcessor.loadFile(file)
                .onSuccess { newImage ->
                    setProject {
                        copy(
                            sourceImages = sourceImages + newImage,
                            selectedSourceIndex = sourceImages.size // 这里的 size 是旧的，正好对应新 index
                        )
                    }
                    // 级联重算流水线
                    val filters = state.project.pipelineSteps.mapNotNull { it.appliedFilter }
                    filterProcessor.processChain(newImage, filters)
                        .onSuccess { newSteps ->
                            setProject {
                                copy(
                                    pipelineSteps = newSteps,
                                    selectedPipelineIndex = newSteps.size
                                )
                            }
                            setSegmentation {
                                copy(
                                    activeRects = emptyList(),
                                    segmentationResults = emptyList()
                                )
                            }
                        }
                }
        }

    }
}

data class SaveProject(val file: File) : ImageUiEvent {
    override fun ImageActionScope.execute() {
        launch {
            projectProcessor.saveProject(
                file,
                state.project.sourceImages,
                state.project.pipelineSteps
            )
                .onSuccess { showToast("工程已保存") }
                .onFailure { showToast("保存失败") }
        }
    }
}

data class LoadProject(val file: File) : ImageUiEvent {
    override fun ImageActionScope.execute() {
        launch {
            projectProcessor.loadProject(file)
                .onSuccess { result ->
                    setProject { copy(sourceImages = result.sourceImages, selectedSourceIndex = 0) }
                    val firstImage = result.sourceImages.first()
                    filterProcessor.processChain(firstImage, result.filters)
                        .onSuccess { steps ->
                            setProject {
                                copy(
                                    pipelineSteps = steps,
                                    selectedPipelineIndex = steps.size
                                )
                            }
                        }
                }
        }
    }
}

data class ExportImage(val file: File) : ImageUiEvent {
    override fun ImageActionScope.execute() {
        launch {
            val bitmap = state.activeDisplayImage?.bufferedImage
            if (bitmap != null) {
                runCatching { ImageIO.write(bitmap, "png", file) }
                    .onSuccess { showToast("导出成功") }
                    .onFailure { showToast("导出失败: ${it.message}") }
            }
        }
    }
}

// 资源管理事件
data class SelectSourceImage(val index: Int) : ImageUiEvent {
    override fun ImageActionScope.execute() {
        val targetImage = state.project.sourceImages.getOrNull(index) ?: return
        launch {
            setProject { copy(selectedSourceIndex = index) }
            val filters = state.project.pipelineSteps.mapNotNull { it.appliedFilter }
            filterProcessor.processChain(targetImage, filters)
                .onSuccess { newSteps ->
                    setProject {
                        copy(
                            pipelineSteps = newSteps,
                            selectedPipelineIndex = newSteps.size
                        )
                    }
                    setSegmentation { copy(activeRects = emptyList()) }
                }
        }
    }
}

data class RemoveSourceImage(val index: Int) : ImageUiEvent {
    override fun ImageActionScope.execute() {
        val currentList = state.project.sourceImages.toMutableList()
        if (index in currentList.indices) {
            currentList.removeAt(index)
            var newIndex = state.project.selectedSourceIndex
            // 简单的索引修正逻辑...
            if (newIndex == index) newIndex = if (currentList.isNotEmpty()) 0 else -1
            else if (newIndex > index) newIndex--

            val reset = (state.project.selectedSourceIndex == index)
            setProject {
                copy(
                    sourceImages = currentList,
                    selectedSourceIndex = newIndex,
                    pipelineSteps = if (reset) emptyList() else pipelineSteps,
                )
            }
            setSegmentation {
                copy(
                    activeRects = if (reset) emptyList() else activeRects,
                    segmentationResults = if (reset) emptyList() else segmentationResults
                )
            }
        }
    }
}

// 截图事件
object StartScreenCapture : ImageUiEvent {
    override fun ImageActionScope.execute() {
        launch {
            resourceProcessor.captureScreen()
                .onSuccess { capture ->
                    setUi { copy(fullScreenCapture = capture.bufferedImage, isScreenCropperVisible = true) }
                }
        }
    }
}

data class ConfirmScreenCrop(val image: java.awt.image.BufferedImage) : ImageUiEvent {
    override fun ImageActionScope.execute() {
        launch {
            val newWorkImage = WorkImage(bufferedImage = image, name = "Capture_${System.currentTimeMillis()}")
            setProject { copy(sourceImages = sourceImages + newWorkImage) }
            val filters = state.project.pipelineSteps.mapNotNull { it.appliedFilter }
            filterProcessor.processChain(newWorkImage, filters)
                .onSuccess { newSteps ->
                    setProject {
                        copy(
                            selectedSourceIndex = sourceImages.lastIndex,
                            pipelineSteps = newSteps,
                            selectedPipelineIndex = newSteps.size,
                        )
                    }
                    setUi {
                        copy(
                            isScreenCropperVisible = false
                        )
                    }
                }
        }
    }
}