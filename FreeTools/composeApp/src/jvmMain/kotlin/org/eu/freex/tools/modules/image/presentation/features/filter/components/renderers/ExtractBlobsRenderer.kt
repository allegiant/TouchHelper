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
        val current = filter as? ExtractBlobsFilter ?: return

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

            // --- 顶部标题与说明 ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "连通域筛选 (提取色块)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                HelpTooltip(
                    description = "基于形状属性提取目标。先对图像进行连通性分析，然后根据宽、高、面积剔除不符合要求的杂块。\n\n" +
                            "• 8向连通：对角线相邻也被视为同一个物体（适合文字）。\n" +
                            "• 4向连通：只有上下左右相邻才算同一个物体（适合分离紧凑的像素）。"
                )
            }

            // --- 1. 核心控制：八向穿透 (参考 BinarizationRenderer 的 RgbAvgControl) ---
            ConnectivityControl(
                isEnabled = current.useEightConnectivity,
                onChange = { onFilterChange(current.copy(useEightConnectivity = it)) }
            )

            // --- 2. 宽度筛选 ---
            RangeControl(
                label = "宽度范围 (Width)",
                values = current.minWidth..current.maxWidth,
                rangeLimit = 0f..current.limitWidth,
                onValueChange = { start, end ->
                    onFilterChange(current.copy(minWidth = start, maxWidth = end))
                }
            )

            // --- 3. 高度筛选 ---
            RangeControl(
                label = "高度范围 (Height)",
                values = current.minHeight..current.maxHeight,
                rangeLimit = 0f..current.limitHeight,
                onValueChange = { start, end ->
                    onFilterChange(current.copy(minHeight = start, maxHeight = end))
                }
            )

            // --- 4. 面积筛选 (去除噪点的神器) ---
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
     * 连通性控制组件 (八向穿透)
     */
    @Composable
    private fun ConnectivityControl(
        isEnabled: Boolean,
        onChange: (Boolean) -> Unit
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onChange(!isEnabled) }
                // 增加一点背景或边距让它看起来像是一个独立的设置项
                .padding(vertical = 4.dp)
        ) {
            Checkbox(
                checked = isEnabled,
                onCheckedChange = onChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.outline
                )
            )
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    text = "使用八向穿透 (8-Connectivity)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isEnabled) "开启：适合汉字、断笔修复" else "关闭：严格 4 邻域 (上下左右)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    /**
     * 封装的范围滑块组件
     * (保持了原有的本地 State 优化逻辑，确保滑动流畅)
     */
    @Composable
    private fun RangeControl(
        label: String,
        values: ClosedFloatingPointRange<Float>,
        rangeLimit: ClosedFloatingPointRange<Float>,
        onValueChange: (Float, Float) -> Unit
    ) {
        // 使用本地状态来驱动滑块 UI，避免等待预览计算回传导致的视觉卡顿
        var sliderPosition by remember(values) { mutableStateOf(values) }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    text = "${sliderPosition.start.roundToInt()} ~ ${sliderPosition.endInclusive.roundToInt()}",
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