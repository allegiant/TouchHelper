package org.eu.freex.tools.modules.image.presentation.viewmodel

import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.eu.freex.tools.model.WorkImage
import org.eu.freex.tools.modules.image.domain.repository.ImageRepository
import org.eu.freex.tools.modules.image.presentation.contract.ImageUiEvent
import org.eu.freex.tools.modules.image.presentation.contract.ImageUiState
import org.eu.freex.tools.utils.ImageUtils

/**
 * 资源处理器：负责文件加载、资源管理、截图
 */
class ResourceHandler(
    private val scope: CoroutineScope,
    private val repository: ImageRepository,
    private val stateFlow: MutableStateFlow<ImageUiState>,
    private val onError: (String) -> Unit
) {

    fun loadFile(event: ImageUiEvent.LoadFile) {
        scope.launch {
            stateFlow.update { it.copy(isLoading = true) }
            try {
                val image = repository.loadFile(event.file)
                stateFlow.update {
                    if (image != null) {
                        val newList = it.sourceImages + image
                        it.copy(
                            sourceImages = newList,
                            selectedSourceIndex = newList.lastIndex,
                            pipelineSteps = emptyList(),
                            isLoading = false
                        )
                    } else {
                        onError("无法加载图片文件: ${event.file.name}")
                        it.copy(isLoading = false)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                stateFlow.update { it.copy(isLoading = false) }
                onError("加载文件出错: ${e.message}")
            }
        }
    }

    fun selectSource(index: Int) {
        stateFlow.update {
            it.copy(
                selectedSourceIndex = index,
                pipelineSteps = emptyList(),
                activeRects = emptyList(),
                segmentationResults = emptyList(),
                binaryPreview = null
            )
        }
    }

    fun removeSource(index: Int) {
        val currentState = stateFlow.value
        val currentList = currentState.sourceImages.toMutableList()
        if (index in currentList.indices) {
            currentList.removeAt(index)
            stateFlow.update { state ->
                var newIndex = state.selectedSourceIndex
                if (newIndex == index) newIndex = if (currentList.isNotEmpty()) (index - 1).coerceAtLeast(0) else -1
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

    fun startCapture() {
        scope.launch(Dispatchers.IO) {
            try { Thread.sleep(300) } catch (e: Exception) {}
            try {
                val capture = ImageUtils.captureFullScreen()
                stateFlow.update { it.copy(fullScreenCapture = capture, isScreenCropperVisible = true) }
            } catch (e: Exception) {
                e.printStackTrace()
                // 不要在 IO 线程直接调 UI 回调，虽然 Channel 是线程安全的，但为了规范
                scope.launch { onError("截图失败: ${e.message}") }
            }
        }
    }

    fun saveScreenCapture(image: java.awt.image.BufferedImage) {
        val newWorkImage = WorkImage(
            bitmap = image.toComposeImageBitmap(),
            bufferedImage = image,
            name = "ScreenCapture_${System.currentTimeMillis()}"
        )
        stateFlow.update {
            val newList = it.sourceImages + newWorkImage
            it.copy(
                sourceImages = newList,
                selectedSourceIndex = newList.lastIndex,
                isScreenCropperVisible = false
            )
        }
    }
}