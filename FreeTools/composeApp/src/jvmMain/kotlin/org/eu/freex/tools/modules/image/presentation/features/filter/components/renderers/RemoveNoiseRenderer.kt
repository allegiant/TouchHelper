package org.eu.freex.tools.modules.image.presentation.features.filter.components.renderers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.common.components.HelpTooltip
import org.eu.freex.tools.modules.image.domain.model.ImageFilter
import org.eu.freex.tools.modules.image.domain.model.RemoveNoiseFilter

object RemoveNoiseRenderer : FilterRenderer {

    @Composable
    override fun Content(
        filter: ImageFilter,
        onFilterChange: (ImageFilter) -> Unit
    ) {
        // 安全类型转换
        val currentFilter = filter as? RemoveNoiseFilter ?: return

        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp) // 增加垂直间距，布局更宽松
        ) {

            // --- 1. 阈值范围 (面积) ---
            ControlGroup(
                label = "阈值范围 (最大面积)",
                description = "定义“杂点”的大小标准。\n\n例如设为 6，则图像中所有【像素数量 ≤ 6】的独立小色块都会被视为噪点并被擦除。数值越大，清理力度越强，但可能会误删标点符号（如句号）。",
                displayValue = "${currentFilter.minArea} px"
            ) {
                Slider(
                    value = currentFilter.minArea.toFloat(),
                    onValueChange = { onFilterChange(currentFilter.copy(minArea = it.toInt())) },
                    valueRange = 0f..20f, // 0~20 通常够用了
                    steps = 19, // 步长为 1
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }

            // --- 2. 间隙数值 ---
            ControlGroup(
                label = "间隙数值 (容差)",
                description = "用于保护断裂的笔画。\n\n• 0：严格模式，像素必须紧挨着才算整体。\n• > 0：算法会先将色块“膨胀”，尝试连接断开的笔画。这能防止文字因笔画中间有微小断点而被误判为两个小杂点。",
                displayValue = if (currentFilter.gap == 0) "关闭 (0)" else "${currentFilter.gap} px"
            ) {
                Slider(
                    value = currentFilter.gap.toFloat(),
                    onValueChange = { onFilterChange(currentFilter.copy(gap = it.toInt())) },
                    valueRange = 0f..5f,
                    steps = 4,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }

            // --- 3. 去除模式 ---
            Column {
                LabelWithTooltip(
                    label = "去除模式",
                    description = "选择要清除的颜色极性：\n\n• 白色点去除：背景为黑色，擦除白色噪点（适合二值化后的黑底白字）。\n• 黑色点去除：背景为白色，擦除黑色污点（适合文档扫描类的白底黑字）。"
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 选项 A: 去除白色
                    FilterRadioButton(
                        text = "白色点去除",
                        selected = currentFilter.removeWhite,
                        onClick = { onFilterChange(currentFilter.copy(removeWhite = true)) }
                    )
                    Spacer(modifier = Modifier.width(24.dp))
                    // 选项 B: 去除黑色
                    FilterRadioButton(
                        text = "黑色点去除",
                        selected = !currentFilter.removeWhite,
                        onClick = { onFilterChange(currentFilter.copy(removeWhite = false)) }
                    )
                }
            }
        }
    }

    /**
     * 封装控件组：标题 + 说明 + 当前值 + 控件内容
     */
    @Composable
    private fun ControlGroup(
        label: String,
        description: String,
        displayValue: String,
        content: @Composable () -> Unit
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：带说明的标题
                LabelWithTooltip(label, description)
                // 右侧：当前数值
                Text(
                    text = displayValue,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            // 下方控件
            content()
        }
    }

    /**
     * 带问号说明的文本标签
     */
    @Composable
    private fun LabelWithTooltip(label: String, description: String) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            HelpTooltip(description)
        }
    }

    /**
     * 简单的单选按钮封装
     */
    @Composable
    private fun FilterRadioButton(
        text: String,
        selected: Boolean,
        onClick: () -> Unit
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(onClick = onClick)
        ) {
            RadioButton(
                selected = selected,
                onClick = null, // 点击事件交给 Row 处理
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = MaterialTheme.colorScheme.outline
                )
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}