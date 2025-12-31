package org.eu.freex.tools.modules.image.presentation.ui.components.inspector.impl

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.modules.image.domain.model.BinarizationFilter
import org.eu.freex.tools.modules.image.presentation.contract.events.UpdateFilter
import org.eu.freex.tools.modules.image.presentation.ui.components.inspector.core.FilterRenderer
import org.eu.freex.tools.modules.image.presentation.ui.components.inspector.core.LocalImageViewModel

object BinarizationRenderer : FilterRenderer {

    @Composable
    override fun Content() {
        val viewModel = LocalImageViewModel.current
        val state by viewModel.uiState.collectAsState()

        // 安全获取参数，如果类型不对则使用默认值
        val filter = state.project.currentFilter as? BinarizationFilter ?: BinarizationFilter()

        Column {
            ThresholdControl(
                range = filter.min..filter.max,
                onValueChange = { newRange ->
                    // 【修改 3】使用 data class 的 copy 方法更新参数
                    // 这里创建了一个全新的 AppFilter 对象
                    val newFilter = filter.copy(
                        min = newRange.start,
                        max = newRange.endInclusive
                    )

                    // 【修改 4】发送事件，将新滤镜对象传回 ViewModel
                    viewModel.handleEvent(UpdateFilter(newFilter))
                }
            )
            Spacer(Modifier.height(10.dp))
            RgbAvgControl(
                isEnabled = filter.isRgbAvg,
                onChange = { isChecked ->
                    val newFilter = filter.copy(isRgbAvg = isChecked)
                    viewModel.handleEvent(UpdateFilter(newFilter))
                }
            )
        }
    }
}

/**
 * 阈值调节滑块组件
 */
@Composable
private fun ThresholdControl(
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "阈值范围",
                color = MaterialTheme.colorScheme.onSurfaceVariant, // 【修改】使用语义颜色
                style = MaterialTheme.typography.labelMedium // 【修改】使用语义排版
            )
            Text(
                "${range.start.toInt()} - ${range.endInclusive.toInt()}",
                color = MaterialTheme.colorScheme.onSurfaceVariant, // 【修改】
                style = MaterialTheme.typography.labelMedium
            )
        }
        RangeSlider(
            value = range,
            onValueChange = onValueChange,
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary, // 【修改】主色
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant // 【修改】适配深/浅模式的轨道色
            )
        )
    }
}

/**
 * RGB 平均值开关组件
 */
@Composable
private fun RgbAvgControl(
    isEnabled: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { onChange(!isEnabled) }
    ) {
        Checkbox(
            checked = isEnabled,
            onCheckedChange = onChange,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary, // 【修改】
                uncheckedColor = MaterialTheme.colorScheme.outline // 【修改】未选中时的边框色
            )
        )
        Text(
            "使用 RGB 平均值",
            color = MaterialTheme.colorScheme.onSurface, // 【修改】确保文字清晰
            style = MaterialTheme.typography.bodyMedium
        )
    }
}