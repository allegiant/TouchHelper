package org.eu.freex.tools.modules.image.presentation.features.filter.components.renderers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.eu.freex.tools.common.components.ColorRuleListPanel
import org.eu.freex.tools.common.model.PickEvent
import org.eu.freex.tools.common.model.PickingToolState
import org.eu.freex.tools.common.utils.toHexString
import org.eu.freex.tools.modules.image.domain.model.ImageFilter
import org.eu.freex.tools.modules.image.domain.model.MultiColorFilter
import org.eu.freex.tools.modules.image.presentation.viewmodel.ImageWorkbenchViewModel
import org.koin.compose.koinInject

object MultiColorRenderer : FilterRenderer {

    @Composable
    override fun Content(filter: ImageFilter, onFilterChange: (ImageFilter) -> Unit) {
        val currentFilter = filter as? MultiColorFilter ?: return
        val workbenchViewModel: ImageWorkbenchViewModel = koinInject()

        // 使用 rememberUpdatedState 确保在 Effect 中使用的是最新的回调和状态
        val currentFilterState by rememberUpdatedState(currentFilter)
        val onFilterChangeState by rememberUpdatedState(onFilterChange)

        val scope = rememberCoroutineScope()

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

            // --- 全局选项 ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FilterCheckbox("背景色 (反色)", currentFilter.isInvert) {
                    onFilterChange(currentFilter.copy(isInvert = it))
                }
                FilterCheckbox("颜色选留 (原色)", currentFilter.keepOriginal) {
                    onFilterChange(currentFilter.copy(keepOriginal = it))
                }
            }

            // --- 颜色列表 (调用公共组件) ---
            ColorRuleListPanel(
                rules = currentFilter.rules,
                onRulesChange = { newRules ->
                    onFilterChange(currentFilter.copy(rules = newRules))
                },
                onRequestPickColor = { index ->
                    scope.launch {
                        workbenchViewModel.activeTool(PickingToolState.ColorPicker)
                        val event = workbenchViewModel.pickEvent.filterIsInstance<PickEvent.ColorPicked>().first()
                        val rules = currentFilterState.rules
                        if (index in rules.indices) {
                            val newHex = event.color.toHexString()
                            val newRules = rules.toMutableList()
                            newRules[index] = newRules[index].copy(targetHex = newHex)
                            onFilterChangeState(currentFilterState.copy(rules = newRules))
                        }
                        workbenchViewModel.activeTool(PickingToolState.None)
                    }
                }
            )
        }
    }

    @Composable
    private fun FilterCheckbox(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onCheckedChange(!checked) }
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.scale(0.8f).size(32.dp)
            )
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}