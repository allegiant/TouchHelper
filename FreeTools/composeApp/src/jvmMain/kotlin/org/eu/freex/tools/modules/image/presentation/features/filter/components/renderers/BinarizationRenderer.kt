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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.common.components.HelpTooltip
import org.eu.freex.tools.modules.image.domain.model.BinarizationFilter
import org.eu.freex.tools.modules.image.domain.model.BinarizationMode
import org.eu.freex.tools.modules.image.domain.model.ImageFilter
import kotlin.math.roundToInt

object BinarizationRenderer : FilterRenderer {

    @Composable
    override fun Content(
        filter: ImageFilter,
        onFilterChange: (ImageFilter) -> Unit
    ) {
        val currentFilter = filter as? BinarizationFilter ?: return

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

            // --- 模式 1: 手动 ---
            ModeSelectionRow(
                text = "RGB平均阈值 (手动)",
                // 【新增】说明文案
                description = "最基础的模式。适合背景颜色单一、特征明显的截图。需要手动拖动滑块来选中想要的颜色范围。",
                selected = currentFilter.mode == BinarizationMode.MANUAL,
                onClick = { onFilterChange(currentFilter.copy(mode = BinarizationMode.MANUAL)) }
            )

            AnimatedVisibility(
                visible = currentFilter.mode == BinarizationMode.MANUAL,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(start = 32.dp, bottom = 8.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ThresholdSlider(
                        min = currentFilter.min,
                        max = currentFilter.max,
                        onValueChange = { newMin, newMax ->
                            onFilterChange(currentFilter.copy(min = newMin, max = newMax))
                        }
                    )
                    RgbAvgControl(
                        isEnabled = currentFilter.isRgbAvg,
                        onChange = { onFilterChange(currentFilter.copy(isRgbAvg = it)) }
                    )
                }
            }

            // --- 模式 2: 智能 (Sauvola) ---
            ModeSelectionRow(
                text = "智能 (Sauvola / 点数均衡)",
                // 【新增】说明文案
                description = "高级模式。专为解决“光照不均”和“阴影”设计。它能根据局部窗口内的对比度自动计算阈值，特别适合拍摄的文档或有复杂底纹的图片。",
                selected = currentFilter.mode == BinarizationMode.ADAPTIVE,
                onClick = { onFilterChange(currentFilter.copy(mode = BinarizationMode.ADAPTIVE)) }
            )

            AnimatedVisibility(
                visible = currentFilter.mode == BinarizationMode.ADAPTIVE,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(start = 32.dp, bottom = 8.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SingleValueSlider(
                        label = "敏感度 (K值)",
                        value = currentFilter.sauvolaK,
                        valueRange = 0.0f..0.5f,
                        displayValue = String.format("%.2f", currentFilter.sauvolaK),
                        onValueChange = { onFilterChange(currentFilter.copy(sauvolaK = it)) }
                    )
                    val winSizeInt = currentFilter.windowSize.roundToInt()
                    SingleValueSlider(
                        label = "计算范围 (窗口大小)",
                        value = currentFilter.windowSize,
                        valueRange = 3f..51f,
                        displayValue = "$winSizeInt px",
                        onValueChange = { onFilterChange(currentFilter.copy(windowSize = it)) }
                    )
                }
            }

            // --- 模式 3: 自动 (OTSU) ---
            ModeSelectionRow(
                text = "自动 (OTSU算法)",
                // 【新增】说明文案
                description = "省心模式。算法会自动分析整张图的直方图，找到黑白分离的最佳全局阈值。适合光照均匀、黑白分明的普通截图。",
                selected = currentFilter.mode == BinarizationMode.OTSU,
                onClick = { onFilterChange(currentFilter.copy(mode = BinarizationMode.OTSU)) }
            )
        }
    }

    /**
     * 带说明图标的单选行
     */
    @Composable
    private fun ModeSelectionRow(
        text: String,
        description: String, // 新增说明参数
        selected: Boolean,
        onClick: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .selectable(
                    selected = selected,
                    onClick = onClick,
                    role = Role.RadioButton
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = MaterialTheme.colorScheme.outline
                )
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp)
            )

            // 【新增】帮助图标和 Tooltip
            if (description.isNotEmpty()) {
                HelpTooltip(description = description)
            }
        }
    }

}

/**
 * 阈值范围滑块组件
 */
@Composable
private fun ThresholdSlider(
    min: Float,
    max: Float,
    onValueChange: (Float, Float) -> Unit
) {
    // 使用本地状态来驱动滑块 UI，避免等待预览计算回传导致的视觉卡顿
    // 当外部 min/max 变化时（例如重置或切换滤镜），重新同步
    var sliderPosition by remember(min, max) { mutableStateOf(min..max) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "阈值范围",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "${sliderPosition.start.toInt()} - ${sliderPosition.endInclusive.toInt()}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium
            )
        }
        RangeSlider(
            value = sliderPosition,
            onValueChange = { range ->
                sliderPosition = range
                // 实时触发预览 (ViewModel 内部会有 Job 取消机制来处理高频事件)
                onValueChange(range.start, range.endInclusive)
            },
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!isEnabled) }
    ) {
        Checkbox(
            checked = isEnabled,
            onCheckedChange = onChange,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                uncheckedColor = MaterialTheme.colorScheme.outline
            )
        )
        Text(
            "使用 RGB 平均值",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

/**
 * 通用的单值滑块组件 (用于 K值 和 窗口大小)
 */
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
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}