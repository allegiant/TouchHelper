package org.eu.freex.tools.modules.image.presentation.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.eu.freex.tools.model.ColorRule
import org.eu.freex.tools.model.RuleScope
import org.eu.freex.tools.model.WorkImage
import org.eu.freex.tools.modules.image.domain.repository.ImageRepository
import org.eu.freex.tools.modules.image.presentation.contract.ImageUiState

/**
 * 切割处理器：负责切割计算、规则管理
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
                val (rects, subImages) = repository.segmentImage(
                    source,
                    state.isGridMode,
                    state.gridParams,
                    state.activeColorRules
                )
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

    fun updateColorRule(ruleId: Long, transform: (ColorRule) -> ColorRule) {
        val state = stateFlow.value
        if (state.currentScope == RuleScope.GLOBAL) {
            stateFlow.update { it.copy(globalColorRules = state.globalColorRules.map { r -> if (r.id == ruleId) transform(r) else r }) }
        } else {
            val idx = state.selectedSourceIndex
            val img = state.currentSourceImage ?: return
            val newRules = (img.localColorRules ?: state.globalColorRules).map { r -> if (r.id == ruleId) transform(r) else r }
            updateSourceImage(idx, img.copy(localColorRules = newRules))
        }
    }

    fun removeColorRule(ruleId: Long) {
        val state = stateFlow.value
        if (state.currentScope == RuleScope.GLOBAL) {
            stateFlow.update { it.copy(globalColorRules = state.globalColorRules.filterNot { r -> r.id == ruleId }) }
        } else {
            val idx = state.selectedSourceIndex
            val img = state.currentSourceImage ?: return
            val newRules = (img.localColorRules ?: state.globalColorRules).filterNot { r -> r.id == ruleId }
            updateSourceImage(idx, img.copy(localColorRules = newRules))
        }
    }

    private fun updateSourceImage(index: Int, newImage: WorkImage) {
        stateFlow.update { s ->
            val l = s.sourceImages.toMutableList(); if (index in l.indices) l[index] = newImage; s.copy(sourceImages = l)
        }
    }
}