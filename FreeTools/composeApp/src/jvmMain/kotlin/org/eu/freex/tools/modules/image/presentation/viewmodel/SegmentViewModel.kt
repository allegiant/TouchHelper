package org.eu.freex.tools.modules.image.presentation.viewmodel

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.eu.freex.tools.modules.image.domain.model.SegmentationProject
import org.eu.freex.tools.modules.image.presentation.core.SegmentationEvent
import org.eu.freex.tools.modules.image.presentation.core.SelectChar
import org.eu.freex.tools.modules.image.presentation.core.StopLabeling
import org.eu.freex.tools.modules.image.presentation.core.SubmitLabelAndNext
import org.eu.freex.tools.modules.image.presentation.core.SwitchTab
import org.eu.freex.tools.modules.image.presentation.core.UpdateSegmentationConfig
import org.eu.freex.tools.modules.image.presentation.core.WorkbenchTab

@OptIn(FlowPreview::class)
internal fun ImageViewModel.setupReactiveSegmentation() {
    val imageFlow = uiState.map { it.displayImage }.distinctUntilChanged()
    val configFlow = uiState.map { it.segmentationProject?.config }.distinctUntilChanged().debounce(200)
    val tabFlow = uiState.map { it.activeTab }.distinctUntilChanged()

    combine(imageFlow, configFlow, tabFlow, ::Triple)
        .onEach { (layer, config, tab) ->
            if (tab == WorkbenchTab.SEGMENTATION && layer?.image != null && config != null) {
                useCase.performSegmentation(layer.image, config)
                    .onSuccess { rects ->
                        updateWorkspace {
                            val current = it.segmentation ?: SegmentationProject(config = config)
                            it.copy(segmentation = current.copy(results = rects))
                        }
                    }
                    .onFailure { it.printStackTrace() }
            }
        }.launchIn(viewModelScope)
}

/**
 * ImageViewModel 的资源管理扩展逻辑
 */
internal suspend fun ImageViewModel.handleSegmentEvent(event: SegmentationEvent) {
    when (event) {
        is SwitchTab -> switchTab(event.tab)
        is UpdateSegmentationConfig -> updateConfig(event)
        is SelectChar -> selectChar(event.index)
        is SubmitLabelAndNext -> submitLabel(event.text)
        is StopLabeling -> stopLabeling()
    }
}

private fun ImageViewModel.switchTab(tab: WorkbenchTab) {
    if (tab == WorkbenchTab.SEGMENTATION && getWorkspaceSnapshot().segmentation == null) {
        updateWorkspace { it.copy(segmentation = SegmentationProject()) }
    }
    updateUiState { it.copy(activeTab = tab) }
}

private fun ImageViewModel.updateConfig(event: UpdateSegmentationConfig) {
    updateWorkspace {
        val current = it.segmentation ?: SegmentationProject()
        it.copy(segmentation = current.copy(config = event.config))
    }
}

private fun ImageViewModel.selectChar(index: Int) {
    updateUiState {
        it.copy(
            segmentationInteraction = it.segmentationInteraction.copy(
                selectedIndex = index, isLabeling = true
            )
        )
    }
}

private fun ImageViewModel.submitLabel(text: String) {
    val currentProject = getWorkspaceSnapshot().segmentation ?: return
    val currentIndex = uiState.value.segmentationInteraction.selectedIndex

    if (currentIndex != -1) {
        val newLabels = currentProject.labels.toMutableMap().apply { put(currentIndex, text) }
        updateWorkspace { it.copy(segmentation = currentProject.copy(labels = newLabels)) }

        val nextIndex = (currentIndex + 1).coerceAtMost(currentProject.results.size - 1)
        updateUiState {
            it.copy(
                segmentationInteraction = it.segmentationInteraction.copy(
                    selectedIndex = nextIndex, isLabeling = true
                )
            )
        }
    }
}

private fun ImageViewModel.stopLabeling() {
    updateUiState {
        it.copy(segmentationInteraction = it.segmentationInteraction.copy(isLabeling = false))
    }
}
