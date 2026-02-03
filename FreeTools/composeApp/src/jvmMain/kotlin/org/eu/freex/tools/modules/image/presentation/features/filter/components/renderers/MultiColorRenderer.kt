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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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

        // 记录当前正在取色的规则索引
        var activePickingIndex by remember { mutableStateOf<Int?>(null) }

        // 使用 rememberUpdatedState 确保在 Effect 中使用的是最新的回调和状态
        val currentFilterState by rememberUpdatedState(currentFilter)
        val onFilterChangeState by rememberUpdatedState(onFilterChange)

        // -------------------------------------------------------------
        // [核心优化] 监听取色结果 (原地处理，无需 ViewModel 托管)
        // -------------------------------------------------------------
        LaunchedEffect(Unit) {
            workbenchViewModel.pickEvent.collect { event ->
                val index = activePickingIndex
                println("接收到: color: $event, index: $index")
                if (event is PickEvent.ColorPicked && index != null) {
                    val rules = currentFilterState.rules
                    if (index in rules.indices) {
                        // 1. 使用工具函数转 Hex，简洁安全
                        val newHex = event.color.toHexString()

                        // 2. 更新规则
                        val newRules = rules.toMutableList()
                        newRules[index] = newRules[index].copy(targetHex = newHex)

                        // 3. 提交变更
                        onFilterChangeState(currentFilterState.copy(rules = newRules))

                        // 4. 成功取色后，退出取色模式
                        workbenchViewModel.activeTool(PickingToolState.None)
                    }
                    // 5. 重置本地索引
                    activePickingIndex = null
                }
            }
        }

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
                    // 记录是谁在请求取色，并激活工具
                    activePickingIndex = index
                    workbenchViewModel.activeTool(PickingToolState.ColorPicker)
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