package org.eu.freex.tools.modules.image.presentation.features.filter.components.renderers

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
import org.eu.freex.tools.common.components.HelpTooltip
import org.eu.freex.tools.modules.image.domain.model.ImageFilter
import org.eu.freex.tools.modules.image.domain.model.RemoveLinesFilter
import kotlin.math.roundToInt

object RemoveLinesRenderer : FilterRenderer {

    @Composable
    override fun Content(
        filter: ImageFilter,
        onFilterChange: (ImageFilter) -> Unit
    ) {
        val currentFilter = filter as? RemoveLinesFilter ?: return

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

            // --- 标题与功能说明 ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "形态学去直线设置",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                // 在说明中加入详细的推荐值表
                HelpTooltip(
                    description = "高级去线模式。利用形态学算法（腐蚀/膨胀）识别并移除长条状结构。\n\n" +
                            "优势：\n" +
                            "• 智能区分：相比传统的“像素占比”法，能更好地保留文字笔画（如“一”、“三”）。\n" +
                            "• 抗干扰：支持去除虚线或粗细不均的线条。\n\n" +
                            "推荐阈值 (Min Length)：\n" +
                            "• 720p及以下 : 30 - 40 px\n" +
                            "• 1080p / 2K : 50 - 60 px\n" +
                            "• 4K / 高分屏 : 80 - 100 px"
                )
            }

            // --- 核心参数：最小长度 ---
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "最小线条长度 (阈值)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${currentFilter.minLength} px",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Slider(
                    value = currentFilter.minLength.toFloat(),
                    onValueChange = {
                        onFilterChange(currentFilter.copy(minLength = it.roundToInt()))
                    },
                    valueRange = 10f..200f,
                    steps = 19,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )

                // 底部保留一条最关键的原则提示
                Text(
                    text = "原则：值应略大于单个汉字的宽度，否则可能误删长横笔画。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // --- 方向选择 ---
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "去除方向",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    DirectionCheckbox(
                        label = "水平方向 (横线)",
                        checked = currentFilter.removeHorizontal,
                        onCheckedChange = {
                            onFilterChange(currentFilter.copy(removeHorizontal = it))
                        }
                    )

                    DirectionCheckbox(
                        label = "垂直方向 (竖线)",
                        checked = currentFilter.removeVertical,
                        onCheckedChange = {
                            onFilterChange(currentFilter.copy(removeVertical = it))
                        }
                    )
                }
            }
        }
    }

    @Composable
    private fun DirectionCheckbox(
        label: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.outline
                )
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}