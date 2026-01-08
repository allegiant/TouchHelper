package org.eu.freex.tools.modules.image.presentation.features.filter.components.renderers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.common.components.HelpTooltip
import org.eu.freex.tools.modules.image.domain.model.ImageFilter
import org.eu.freex.tools.modules.image.domain.model.RotationFilter

object RotationRenderer : FilterRenderer {

    @Composable
    override fun Content(
        filter: ImageFilter,
        onFilterChange: (ImageFilter) -> Unit
    ) {
        val currentFilter = filter as? RotationFilter ?: return

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

            // --- 模式 1: 自动纠正 ---
            ModeSelectionRow(
                text = "自动纠正 (投影分析)",
                description = "算法会自动分析文字行的排列方向，计算出最佳水平角度。适合处理扫描件或稍微歪斜的游戏截图。",
                selected = currentFilter.isAuto,
                onClick = { onFilterChange(currentFilter.copy(isAuto = true)) }
            )

            AnimatedVisibility(
                visible = currentFilter.isAuto,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(start = 32.dp, bottom = 8.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 范围滑块
                    SingleValueSlider(
                        label = "搜索范围 (±°)",
                        value = currentFilter.maxSearchRange,
                        valueRange = 5f..45f,
                        displayValue = "±${currentFilter.maxSearchRange.toInt()}°",
                        onValueChange = { onFilterChange(currentFilter.copy(maxSearchRange = it)) }
                    )
                    // 精度滑块
                    SingleValueSlider(
                        label = "检测精度 (步长)",
                        value = currentFilter.precision,
                        valueRange = 0.1f..2.0f,
                        displayValue = String.format("%.1f°", currentFilter.precision),
                        onValueChange = { onFilterChange(currentFilter.copy(precision = it)) }
                    )
                }
            }

            // --- 模式 2: 手动旋转 ---
            ModeSelectionRow(
                text = "固定旋转 (手动)",
                description = "手动指定旋转角度。正数顺时针，负数逆时针。",
                selected = !currentFilter.isAuto,
                onClick = { onFilterChange(currentFilter.copy(isAuto = false)) }
            )

            AnimatedVisibility(
                visible = !currentFilter.isAuto,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(start = 32.dp, bottom = 8.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SingleValueSlider(
                        label = "旋转角度",
                        value = currentFilter.angle,
                        valueRange = -180f..180f,
                        displayValue = String.format("%.1f°", currentFilter.angle),
                        onValueChange = { onFilterChange(currentFilter.copy(angle = it)) }
                    )
                }
            }
        }
    }

    @Composable
    private fun ModeSelectionRow(
        text: String,
        description: String,
        selected: Boolean,
        onClick: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp)
            )
            if (description.isNotEmpty()) {
                HelpTooltip(description = description)
            }
        }
    }

    @Composable
    private fun SingleValueSlider(
        label: String,
        value: Float,
        valueRange: ClosedFloatingPointRange<Float>,
        displayValue: String,
        onValueChange: (Float) -> Unit
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, style = MaterialTheme.typography.bodySmall)
                Text(displayValue, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary)
            )
        }
    }
}