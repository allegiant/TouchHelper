package org.eu.freex.tools.modules.image.presentation.viewmodel

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


    fun addSourceImage(workImage: WorkImage) {
        scope.launch {
            stateFlow.update { it.copy(isLoading = true) }
            try {
                if (workImage != null) {
                    stateFlow.update { state ->
                        state.copy(sourceImages = state.sourceImages + workImage)
                    }

                    val currentState = stateFlow.value
                    val newIndex = currentState.sourceImages.lastIndex
                    val oldSteps = currentState.pipelineSteps

                    val newSteps = if (oldSteps.isNotEmpty()) {
                        reapplyPipeline(workImage, oldSteps)
                    } else {
                        emptyList()
                    }

                    stateFlow.update {
                        it.copy(
                            selectedSourceIndex = newIndex,
                            pipelineSteps = newSteps,
                            selectedPipelineIndex = newSteps.size,
                            activeRects = emptyList(),
                            segmentationResults = emptyList(),
                            binaryPreview = null,
                            isLoading = false
                        )
                    }
                } else {
                    stateFlow.update { it.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                stateFlow.update { it.copy(isLoading = false) }
                onError("加载文件出错: ${e.message}")
            }
        }
    }

    fun loadFile(event: ImageUiEvent.LoadFile) {
        scope.launch {
            stateFlow.update { it.copy(isLoading = true) }
            try {
                val image = repository.loadFile(event.file)
                if (image != null) {
                    stateFlow.update { state ->
                        state.copy(sourceImages = state.sourceImages + image)
                    }

                    val currentState = stateFlow.value
                    val newIndex = currentState.sourceImages.lastIndex
                    val oldSteps = currentState.pipelineSteps

                    val newSteps = if (oldSteps.isNotEmpty()) {
                        reapplyPipeline(image, oldSteps)
                    } else {
                        emptyList()
                    }

                    stateFlow.update {
                        it.copy(
                            selectedSourceIndex = newIndex,
                            pipelineSteps = newSteps,
                            selectedPipelineIndex = newSteps.size,
                            activeRects = emptyList(),
                            segmentationResults = emptyList(),
                            binaryPreview = null,
                            isLoading = false
                        )
                    }
                } else {
                    onError("无法加载图片文件: ${event.file.name}")
                    stateFlow.update { it.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                stateFlow.update { it.copy(isLoading = false) }
                onError("加载文件出错: ${e.message}")
            }
        }
    }

    fun selectSource(index: Int) {
        val currentState = stateFlow.value
        if (index == currentState.selectedSourceIndex) return

        val targetImage = currentState.sourceImages.getOrNull(index) ?: return
        val oldSteps = currentState.pipelineSteps

        scope.launch {
            // 【关键修复】只有当确实有流水线步骤需要重算时，才显示 Loading
            // 避免在浏览原图（无步骤）时出现闪烁
            val needProcessing = oldSteps.isNotEmpty()

            if (needProcessing) {
                stateFlow.update { it.copy(isLoading = true) }
            }

            // 尝试重放流水线
            val newSteps = if (needProcessing) {
                reapplyPipeline(targetImage, oldSteps)
            } else {
                emptyList()
            }

            stateFlow.update {
                it.copy(
                    selectedSourceIndex = index,
                    pipelineSteps = newSteps,
                    selectedPipelineIndex = newSteps.size, // 选中最后一步
                    activeRects = emptyList(),      // 切换图片后，旧的切割框不再适用
                    segmentationResults = emptyList(),
                    binaryPreview = null,
                    isLoading = false
                )
            }
        }
    }

    fun removeSource(index: Int) {
        val currentState = stateFlow.value
        val currentList = currentState.sourceImages.toMutableList()
        if (index in currentList.indices) {
            currentList.removeAt(index)
            stateFlow.update { state ->
                var newIndex = state.selectedSourceIndex
                if (newIndex == index) {
                    newIndex = if (currentList.isNotEmpty()) (index - 1).coerceAtLeast(0) else -1
                } else if (newIndex > index) {
                    newIndex--
                }

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
            try {
                Thread.sleep(300)
            } catch (e: Exception) {
            }
            try {
                val capture = ImageUtils.captureFullScreen()
                stateFlow.update {
                    it.copy(
                        fullScreenCapture = capture,
                        isScreenCropperVisible = true
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                scope.launch { onError("截图失败: ${e.message}") }
            }
        }
    }

    fun saveScreenCapture(image: java.awt.image.BufferedImage) {
        val newWorkImage = WorkImage(
            bufferedImage = image,
            name = "ScreenCapture_${System.currentTimeMillis()}"
        )

        scope.launch {
            stateFlow.update { it.copy(isLoading = true) }

            stateFlow.update {
                it.copy(sourceImages = it.sourceImages + newWorkImage)
            }

            val currentState = stateFlow.value
            val oldSteps = currentState.pipelineSteps
            val newSteps = if (oldSteps.isNotEmpty()) {
                reapplyPipeline(newWorkImage, oldSteps)
            } else {
                emptyList()
            }

            stateFlow.update {
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

    // --- 核心逻辑：重放流水线 ---

    private suspend fun reapplyPipeline(
        source: WorkImage,
        oldSteps: List<WorkImage>
    ): List<WorkImage> {
        val newSteps = mutableListOf<WorkImage>()
        var currentInput = source

        for (step in oldSteps) {
            val filter = step.appliedFilter ?: continue
            try {
                val result = repository.applyFilter(currentInput, filter)
                newSteps.add(result)
                currentInput = result
            } catch (e: Exception) {
                e.printStackTrace()
                break
            }
        }
        return newSteps
    }
}