package org.eu.freex.tools.modules.image.presentation.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.eu.freex.tools.model.AppSegmentation
import org.eu.freex.tools.model.AutoSegmentation
import org.eu.freex.tools.model.GridSegmentation
import org.eu.freex.tools.modules.image.domain.repository.ImageRepository
import org.eu.freex.tools.modules.image.presentation.contract.ImageUiState

/**
 * 切割处理器：负责切割计算
 */
class SegmentationHandler(
    private val scope: CoroutineScope,
    private val repository: ImageRepository,
    private val stateFlow: MutableStateFlow<ImageUiState>,
    private val onError: (String) -> Unit
) {

    fun performSegmentation() {
        val state = stateFlow.value
        val source = state.activeDisplayImage ?: return
        scope.launch {
            stateFlow.update { it.copy(isLoading = true) }
            try {
                val segmentationStrategy: AppSegmentation = if (state.isGridMode) {
                    GridSegmentation(state.gridParams)
                } else {
                    AutoSegmentation(rules = emptyList()) // 或者传入 state.activeRules
                }

                val (rects, subImages) = repository.segmentImage(source, segmentationStrategy)

                stateFlow.update {
                    it.copy(
                        activeRects = rects,
                        segmentationResults = subImages,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                stateFlow.update { it.copy(isLoading = false) }
                onError("切割识别出错: ${e.message}")
            }
        }
    }
}