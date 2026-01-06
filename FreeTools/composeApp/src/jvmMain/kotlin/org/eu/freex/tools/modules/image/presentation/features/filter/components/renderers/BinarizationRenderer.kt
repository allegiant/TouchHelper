package org.eu.freex.tools.modules.image.presentation.features.filter.components.renderers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.modules.image.domain.model.BinarizationFilter
import org.eu.freex.tools.modules.image.domain.model.ImageFilter

object BinarizationRenderer : FilterRenderer {

    @Composable
    override fun Content(
        filter: ImageFilter,
        onFilterChange: (ImageFilter) -> Unit
    ) {
        // 2. 从 Draft 中提取当前正在编辑的滤镜参数
        // FilterUIRegistry 已经保证了类型匹配，但为了安全这里做一次转换
        val currentFilter = filter as? BinarizationFilter

        if (currentFilter == null) {
            Text("参数加载错误", color = MaterialTheme.colorScheme.error)
            return
        }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // 3. 阈值范围滑块
            ThresholdSlider(
                min = currentFilter.min,
                max = currentFilter.max,
                onValueChange = { newMin, newMax ->
                    // 发送预览事件：仅更新草稿和预览图，不提交到流水线
                    onFilterChange(currentFilter.copy(min = newMin, max = newMax))
                }
            )

            // 4. RGB 平均值开关
            RgbAvgControl(
                isEnabled = currentFilter.isRgbAvg,
                onChange = { newValue ->
                    onFilterChange(currentFilter.copy(isRgbAvg = newValue))
                }
            )
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