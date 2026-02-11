package org.eu.freex.tools.modules.image.presentation.features.filter.components.renderers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.eu.freex.tools.common.model.PickEvent
import org.eu.freex.tools.common.model.PickingToolState
import org.eu.freex.tools.common.utils.toHexString
import org.eu.freex.tools.modules.image.domain.model.ImageFilter
import org.eu.freex.tools.modules.image.domain.model.MultiColorFilter
import org.eu.freex.tools.modules.image.presentation.components.shared.MultiColorRuleEditor
import org.eu.freex.tools.modules.image.presentation.viewmodel.ImageWorkbenchViewModel
import org.koin.compose.koinInject

object MultiColorRenderer : FilterRenderer {

    @Composable
    override fun Content(filter: ImageFilter, onFilterChange: (ImageFilter) -> Unit) {
        val currentFilter = filter as? MultiColorFilter ?: return
        val workbenchViewModel: ImageWorkbenchViewModel = koinInject()

        val currentFilterState by rememberUpdatedState(currentFilter)
        val onFilterChangeState by rememberUpdatedState(onFilterChange)
        val scope = rememberCoroutineScope()

        MultiColorRuleEditor(
            isInvert = currentFilter.isInvert,
            keepOriginal = currentFilter.keepOriginal,
            rules = currentFilter.rules,
            onInvertChange = { checked ->
                onFilterChange(currentFilter.copy(isInvert = checked))
            },
            onKeepOriginalChange = { checked ->
                onFilterChange(currentFilter.copy(keepOriginal = checked))
            },
            onRulesChange = { newRules ->
                onFilterChange(currentFilter.copy(rules = newRules))
            },
            onRequestPickColor = { index ->
                scope.launch {
                    workbenchViewModel.activeTool(PickingToolState.ColorPicker)
                    try {
                        val event = workbenchViewModel.pickEvent
                            .filterIsInstance<PickEvent.ColorPicked>()
                            .first()

                        val rules = currentFilterState.rules
                        if (index in rules.indices) {
                            val newRules = rules.toMutableList()
                            newRules[index] = newRules[index].copy(targetHex = event.color.toHexString())
                            onFilterChangeState(currentFilterState.copy(rules = newRules))
                        }
                    } finally {
                        workbenchViewModel.activeTool(PickingToolState.None)
                    }
                }
            }
        )
    }
}