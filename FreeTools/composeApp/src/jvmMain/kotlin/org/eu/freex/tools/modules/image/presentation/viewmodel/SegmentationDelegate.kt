package org.eu.freex.tools.modules.image.presentation.viewmodel

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

class SegmentationDelegate(private val context: ViewModelContext) {

    init {
        setupReactiveSegmentation()
    }

    fun handle(event: SegmentationEvent) {
        when (event) {
            is SwitchTab -> switchTab(event.tab)
            is UpdateSegmentationConfig -> updateConfig(event)
            is SelectChar -> selectChar(event.index)
            is SubmitLabelAndNext -> submitLabel(event.text)
            is StopLabeling -> stopLabeling()
        }
    }

    private fun switchTab(tab: WorkbenchTab) {
        if (tab == WorkbenchTab.SEGMENTATION && context.getWorkspaceSnapshot().segmentation == null) {
            context.updateWorkspace { copy(segmentation = SegmentationProject()) }
        }
        context.updateUiState { it.copy(activeTab = tab) }
    }

    private fun updateConfig(event: UpdateSegmentationConfig) {
        context.updateWorkspace {
            val current = segmentation ?: SegmentationProject()
            copy(segmentation = current.copy(config = event.config))
        }
    }

    private fun selectChar(index: Int) {
        context.updateUiState {
            it.copy(segmentationInteraction = it.segmentationInteraction.copy(
                selectedIndex = index, isLabeling = true
            ))
        }
    }

    private fun submitLabel(text: String) {
        val currentProject = context.getWorkspaceSnapshot().segmentation ?: return
        val currentIndex = context.uiState.value.segmentationInteraction.selectedIndex

        if (currentIndex != -1) {
            val newLabels = currentProject.labels.toMutableMap().apply { put(currentIndex, text) }
            context.updateWorkspace { copy(segmentation = currentProject.copy(labels = newLabels)) }

            val nextIndex = (currentIndex + 1).coerceAtMost(currentProject.results.size - 1)
            context.updateUiState {
                it.copy(segmentationInteraction = it.segmentationInteraction.copy(
                    selectedIndex = nextIndex, isLabeling = true
                ))
            }
        }
    }

    private fun stopLabeling() {
        context.updateUiState {
            it.copy(segmentationInteraction = it.segmentationInteraction.copy(isLabeling = false))
        }
    }

    @OptIn(FlowPreview::class)
    private fun setupReactiveSegmentation() {
        val imageFlow = context.uiState.map { it.displayImage }.distinctUntilChanged()
        val configFlow = context.uiState.map { it.segmentationProject?.config }.distinctUntilChanged().debounce(200)
        val tabFlow = context.uiState.map { it.activeTab }.distinctUntilChanged()

        combine(imageFlow, configFlow, tabFlow, ::Triple)
            .onEach { (layer, config, tab) ->
                if (tab == WorkbenchTab.SEGMENTATION && layer?.image != null && config != null) {
                    context.useCase.performSegmentation(layer.image, config)
                        .onSuccess { rects ->
                            context.updateWorkspace {
                                val current = segmentation ?: SegmentationProject(config = config)
                                copy(segmentation = current.copy(results = rects))
                            }
                        }
                        .onFailure { it.printStackTrace() }
                }
            }.launchIn(context.scope)
    }
}