package org.eu.freex.tools.modules.image.presentation.features.filter.components.renderers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import org.eu.freex.tools.common.components.HelpTooltip
import org.eu.freex.tools.modules.image.domain.model.ExtractBlobsFilter
import org.eu.freex.tools.modules.image.domain.model.ImageFilter
import kotlin.math.roundToInt

object ExtractBlobsRenderer : FilterRenderer {

    @Composable
    override fun Content(
        filter: ImageFilter,
        onFilterChange: (ImageFilter) -> Unit
    ) {
        // 确保类型转换安全
        val current = filter as? ExtractBlobsFilter ?: return

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

            // 顶部说明
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "连通域筛选 (提取色块)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                HelpTooltip(
                    description = "基于形状属性提取目标。先对图像进行连通性分析，然后根据宽、高、面积剔除不符合要求的杂块。比单纯的“去除噪点”更强大。"
                )
            }

            // 1. 宽度筛选 RangeSlider
            RangeControl(
                label = "宽度范围 (Width)",
                values = current.minWidth..current.maxWidth,
                rangeLimit = 0f..current.limitWidth,
                onValueChange = { start, end ->
                    onFilterChange(current.copy(minWidth = start, maxWidth = end))
                }
            )

            // 2. 高度筛选 RangeSlider
            RangeControl(
                label = "高度范围 (Height)",
                values = current.minHeight..current.maxHeight,
                rangeLimit = 0f..current.limitHeight,
                onValueChange = { start, end ->
                    onFilterChange(current.copy(minHeight = start, maxHeight = end))
                }
            )

            // 3. 面积筛选 RangeSlider
            RangeControl(
                label = "面积范围 (Pixel Area)",
                values = current.minArea..current.maxArea,
                rangeLimit = 0f..current.limitArea,
                onValueChange = { start, end ->
                    onFilterChange(current.copy(minArea = start, maxArea = end))
                }
            )
        }
    }

    /**
     * 封装的范围滑块组件
     */
    @Composable
    private fun RangeControl(
        label: String,
        values: ClosedFloatingPointRange<Float>,
        rangeLimit: ClosedFloatingPointRange<Float>,
        onValueChange: (Float, Float) -> Unit
    ) {
        // 本地 State 优化滑动流畅度
        var sliderPosition by remember(values) { mutableStateOf(values) }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                // 显示当前选中的整数范围
                Text(
                    text = "${sliderPosition.start.roundToInt()} ~ ${sliderPosition.endInclusive.roundToInt()} px",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            RangeSlider(
                value = sliderPosition,
                onValueChange = { range ->
                    sliderPosition = range
                    onValueChange(range.start, range.endInclusive)
                },
                valueRange = rangeLimit,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}