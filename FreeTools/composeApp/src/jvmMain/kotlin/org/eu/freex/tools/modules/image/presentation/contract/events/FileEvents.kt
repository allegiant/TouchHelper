package org.eu.freex.tools.modules.image.presentation.contract.events

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.eu.freex.tools.model.WorkImage
import org.eu.freex.tools.modules.image.presentation.contract.ImageActionScope
import org.eu.freex.tools.modules.image.presentation.contract.ImageUiEvent
import java.io.File
import javax.imageio.ImageIO

class LoadFile(val file: File) : ImageUiEvent {

    override fun execute(action: ImageActionScope) = action.launch {
        action.resourceProcessor.loadFile(file)
            .onSuccess { newImage ->
                action.updateState { it.copy(sourceImages = it.sourceImages + newImage) }

                // 级联重算流水线
                val currentState = action.state
                val filters = currentState.pipelineSteps.mapNotNull { it.appliedFilter }

                action.filterProcessor.processChain(newImage, filters)
                    .onSuccess { newSteps ->
                        action.updateState {
                            it.copy(
                                selectedSourceIndex = currentState.sourceImages.lastIndex,
                                pipelineSteps = newSteps,
                                selectedPipelineIndex = newSteps.size,
                                activeRects = emptyList(),
                                segmentationResults = emptyList(),
                                binaryPreview = null,
                                isLoading = false
                            )
                        }
                    }
            }
            .onFailure { throw it } // 让 launch 统一捕获
    }
}

data class SaveProject(val file: File) : ImageUiEvent {
    override fun execute(action: ImageActionScope) = action.launch {
        action.projectProcessor.saveProject(
            file,
            action.state.sourceImages,
            action.state.pipelineSteps
        ).onSuccess { action.showToast("工程已保存") }
            .onFailure { showToast("保存失败") }
    }
}

data class LoadProject(val file: File) : ImageUiEvent {
    override fun execute(action: ImageActionScope) = action.launch {
        action.projectProcessor.loadProject(file)
            .onSuccess { result ->
                action.updateState {
                    it.copy(sourceImages = result.sourceImages, selectedSourceIndex = 0)
                }
                // 恢复流水线
                val firstImage = result.sourceImages.first()
                action.filterProcessor.processChain(firstImage, result.filters)
                    .onSuccess { steps ->
                        action.updateState {
                            it.copy(pipelineSteps = steps, selectedPipelineIndex = steps.size)
                        }
                    }
            }
    }
}

data class ExportImage(val file: File) : ImageUiEvent {
    override fun execute(action: ImageActionScope) {
        action.scope.launch(Dispatchers.IO) {
            val bitmap = action.state.activeDisplayImage?.bufferedImage
            if (bitmap != null) {
                runCatching { ImageIO.write(bitmap, "png", file) }
                    .onSuccess { action.showToast("导出成功") }
                    .onFailure { action.showToast("导出失败: ${it.message}") }
            }
        }
    }
}

// 资源管理事件
data class SelectSourceImage(val index: Int) : ImageUiEvent {
    override fun execute(action: ImageActionScope) {
        val targetImage = action.state.sourceImages.getOrNull(index) ?: return
        action.launch {
            action.updateState { it.copy(
                isLoading = true,
                selectedSourceIndex = index
            ) }
            // 切换底图，重算流水线
            val filters = action.state.pipelineSteps.mapNotNull { it.appliedFilter }
            action.filterProcessor.processChain(targetImage, filters)
                .onSuccess { newSteps ->
                    action.updateState {
                        it.copy(
                            pipelineSteps = newSteps,
                            selectedPipelineIndex = newSteps.size,
                            activeRects = emptyList(),
                            isLoading = false
                        )
                    }
                }
        }
    }
}

data class RemoveSourceImage(val index: Int) : ImageUiEvent {
    override fun execute(action: ImageActionScope) {
        val currentList = action.state.sourceImages.toMutableList()
        if (index in currentList.indices) {
            currentList.removeAt(index)
            action.updateState { state ->
                var newIndex = state.selectedSourceIndex
                // 简单的索引修正逻辑...
                if (newIndex == index) newIndex = if (currentList.isNotEmpty()) 0 else -1
                else if (newIndex > index) newIndex--

                val reset = (state.selectedSourceIndex == index)
                state.copy(
                    sourceImages = currentList,
                    selectedSourceIndex = newIndex,
                    pipelineSteps = if (reset) emptyList() else state.pipelineSteps,
                    activeRects = if (reset) emptyList() else state.activeRects,
                    segmentationResults = if (reset) emptyList() else state.segmentationResults
                )
            }
        }
    }
}

// 截图事件
object StartScreenCapture : ImageUiEvent {
    override fun execute(action: ImageActionScope) = action.launch {
        action.resourceProcessor.captureScreen()
            .onSuccess { capture ->
                action.updateState {
                    it.copy(fullScreenCapture = capture.bufferedImage, isScreenCropperVisible = true)
                }
            }
    }
}

data class ConfirmScreenCrop(val image: java.awt.image.BufferedImage) : ImageUiEvent {
    override fun execute(action: ImageActionScope) = action.launch {
        val newWorkImage = WorkImage(bufferedImage = image, name = "Capture_${System.currentTimeMillis()}")
        // 复用 LoadFile 里的添加逻辑的一部分，或者直接写
        action.updateState { it.copy(sourceImages = it.sourceImages + newWorkImage) }
        // ... 此处可以类似 LoadFile 进行流水线重算 ...
        val currentState = action.state
        val filters = currentState.pipelineSteps.mapNotNull { it.appliedFilter }
        filterProcessor.processChain(newWorkImage, filters)
            .onSuccess { newSteps ->
                action.updateState {
                    it.copy(
                        selectedSourceIndex = it.sourceImages.lastIndex,
                        pipelineSteps = newSteps,
                        selectedPipelineIndex = newSteps.size,
                        isScreenCropperVisible = false,
                        isLoading = false
                    )
                }
            }
    }
}