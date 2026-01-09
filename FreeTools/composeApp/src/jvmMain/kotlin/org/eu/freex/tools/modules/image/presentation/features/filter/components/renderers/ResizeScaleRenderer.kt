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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.modules.image.domain.model.ImageFilter
import org.eu.freex.tools.modules.image.domain.model.ResizeScaleFilter

object ResizeScaleRenderer : FilterRenderer {

    @Composable
    override fun Content(
        filter: ImageFilter,
        onFilterChange: (ImageFilter) -> Unit
    ) {
        val currentFilter = filter as? ResizeScaleFilter ?: return

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

            // --- 1. 缩放倍率控制 ---
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SingleValueSlider(
                    label = "缩放倍率",
                    value = currentFilter.scaleFactor,
                    valueRange = 0.1f..4.0f,
                    displayValue = String.format("%.1fx", currentFilter.scaleFactor),
                    onValueChange = { onFilterChange(currentFilter.copy(scaleFactor = it)) }
                )

                // 辅助提示文本
                val hint = when {
                    currentFilter.scaleFactor < 1.0f -> "当前操作：缩小图片 (像素减少)"
                    currentFilter.scaleFactor > 1.0f -> "当前操作：放大图片 (像素增加)"
                    else -> "保持原样"
                }
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }

            // --- 2. 采样质量控制 (模仿 RgbAvgControl 风格) ---
            QualityControl(
                isEnabled = currentFilter.highQuality,
                onChange = { onFilterChange(currentFilter.copy(highQuality = it)) }
            )
        }
    }

    /**
     * 采样质量开关组件
     */
    @Composable
    private fun QualityControl(
        isEnabled: Boolean,
        onChange: (Boolean) -> Unit
    ) {
        Row(
            verticalAlignment = Alignment.Top, // 对齐顶部，因为有副标题
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onChange(!isEnabled) }
                .padding(vertical = 4.dp)
        ) {
            Checkbox(
                checked = isEnabled,
                onCheckedChange = onChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier.padding(top = 2.dp) // 微调 Checkbox 位置以对齐第一行文字
            )
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    "高质量采样 (Lanczos3)",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "开启：边缘平滑，适合截图或照片。\n关闭：保留硬边缘(Nearest)，适合像素风或二值图，速度更快。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    lineHeight = MaterialTheme.typography.labelSmall.lineHeight * 1.2
                )
            }
        }
    }

    /**
     * 单值滑块组件 (复用自 BinarizationRenderer 风格)
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
                // 为了让调节更有“档位感”，可以加 steps，也可以不加
                // steps = 38, // 0.1 一格
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}