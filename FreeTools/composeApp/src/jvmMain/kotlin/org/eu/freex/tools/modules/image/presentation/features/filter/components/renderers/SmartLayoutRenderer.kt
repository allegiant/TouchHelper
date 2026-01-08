package org.eu.freex.tools.modules.image.presentation.features.filter.components.renderers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.common.components.HelpTooltip
import org.eu.freex.tools.modules.image.domain.model.ImageFilter
import org.eu.freex.tools.modules.image.domain.model.SmartLayoutFilter // 确保导入了新模型

object SmartLayoutRenderer : FilterRenderer {

    @Composable
    override fun Content(
        filter: ImageFilter,
        onFilterChange: (ImageFilter) -> Unit
    ) {
        val current = filter as? SmartLayoutFilter ?: return

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

            // 标题区
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "智能重排 (栅栏调整)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                HelpTooltip(description = "基于连通域分析技术。自动提取每个字符，消除不均匀的间距，并将它们整齐地排列在一条直线上。无需手动设置切割线。")
            }

            // 1. 核心功能：间距调整
            SimpleSlider(
                label = "字符间距 (Padding)",
                value = current.padding.toFloat(),
                range = 0f..50f,
                displayValue = "${current.padding} px",
                onValueChange = { onFilterChange(current.copy(padding = it.toInt())) }
            )

            // 2. 核心功能：垂直居中 (Switch)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onFilterChange(current.copy(alignCenter = !current.alignCenter)) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("垂直居中对齐", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "将所有字符对齐到水平中线",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Switch(
                    checked = current.alignCenter,
                    onCheckedChange = { onFilterChange(current.copy(alignCenter = it)) },
                    colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                )
            }

            // 3. 高级设置：折叠区域
            // 模仿 BinarizationRenderer 的 AnimatedVisibility 效果
            var showAdvanced = current.fixedHeight > 0 || current.minWidth > 5 || current.minHeight > 5

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(
                    text = "高级过滤设置",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // 噪点过滤 - 宽度
                SimpleSlider(
                    label = "忽略噪点 (最小宽度)",
                    value = current.minWidth.toFloat(),
                    range = 0f..20f,
                    displayValue = "> ${current.minWidth} px",
                    onValueChange = { onFilterChange(current.copy(minWidth = it.toInt())) }
                )

                // 噪点过滤 - 高度
                SimpleSlider(
                    label = "忽略噪点 (最小高度)",
                    value = current.minHeight.toFloat(),
                    range = 0f..20f,
                    displayValue = "> ${current.minHeight} px",
                    onValueChange = { onFilterChange(current.copy(minHeight = it.toInt())) }
                )

                // 固定高度设置
                SimpleSlider(
                    label = "强制画布高度 (0为自动)",
                    value = current.fixedHeight.toFloat(),
                    range = 0f..200f,
                    displayValue = if (current.fixedHeight == 0) "自动" else "${current.fixedHeight} px",
                    onValueChange = { onFilterChange(current.copy(fixedHeight = it.toInt())) }
                )
            }
        }
    }

    /**
     * 简单的滑块组件 (提取自 BinarizationRenderer 的 SingleValueSlider 逻辑)
     */
    @Composable
    private fun SimpleSlider(
        label: String,
        value: Float,
        range: ClosedFloatingPointRange<Float>,
        displayValue: String,
        onValueChange: (Float) -> Unit
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = displayValue,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = range,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}