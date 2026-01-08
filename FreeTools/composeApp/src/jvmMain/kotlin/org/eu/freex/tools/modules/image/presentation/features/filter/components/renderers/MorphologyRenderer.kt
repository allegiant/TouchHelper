package org.eu.freex.tools.modules.image.presentation.features.filter.components.renderers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.common.components.HelpTooltip
import org.eu.freex.tools.modules.image.domain.model.ImageFilter
import org.eu.freex.tools.modules.image.domain.model.MorphologyFilter
import org.eu.freex.tools.modules.image.domain.model.MorphologyMode
import kotlin.math.roundToInt

object MorphologyRenderer : FilterRenderer {

    @Composable
    override fun Content(
        filter: ImageFilter,
        onFilterChange: (ImageFilter) -> Unit
    ) {
        val currentFilter = filter as? MorphologyFilter ?: return

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

            // 1. 模式选择 (使用 RadioButton 组或下拉菜单，这里用 RadioButton 组展示)
            Column {
                Text(
                    "处理模式",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                ModeOption(
                    label = "膨胀 (扩张白色)",
                    desc = "扩大亮色区域。如果是白字则加粗，如果是黑字则变细。",
                    selected = currentFilter.mode == MorphologyMode.DILATE,
                    onClick = { onFilterChange(currentFilter.copy(mode = MorphologyMode.DILATE)) }
                )
                ModeOption(
                    label = "腐蚀 (收缩白色)",
                    desc = "缩小亮色区域。如果是白字则变细，如果是黑字则加粗。",
                    selected = currentFilter.mode == MorphologyMode.ERODE,
                    onClick = { onFilterChange(currentFilter.copy(mode = MorphologyMode.ERODE)) }
                )
                ModeOption(
                    label = "开运算 (去噪)",
                    desc = "先腐蚀后膨胀。消除背景小白点，平滑边界。",
                    selected = currentFilter.mode == MorphologyMode.OPEN,
                    onClick = { onFilterChange(currentFilter.copy(mode = MorphologyMode.OPEN)) }
                )
                ModeOption(
                    label = "闭运算 (连笔)",
                    desc = "先膨胀后腐蚀。连接断开的笔画，填补内部空洞。",
                    selected = currentFilter.mode == MorphologyMode.CLOSE,
                    onClick = { onFilterChange(currentFilter.copy(mode = MorphologyMode.CLOSE)) }
                )
                ModeOption(
                    label = "梯度 (轮廓)",
                    desc = "保留物体的边缘轮廓。",
                    selected = currentFilter.mode == MorphologyMode.GRADIENT,
                    onClick = { onFilterChange(currentFilter.copy(mode = MorphologyMode.GRADIENT)) }
                )
            }

            // 2. 参数调节
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "强度调节",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                // 核大小滑块
                val r = currentFilter.kernelSize
                val sizeStr = "${r * 2 + 1} x ${r * 2 + 1}" // 半径1 -> 3x3
                SliderControl(
                    label = "核大小 (半径: $r)",
                    subLabel = "实际范围: $sizeStr",
                    value = r.toFloat(),
                    range = 1f..10f,
                    steps = 8, // 1..10 离散
                    onValueChange = { onFilterChange(currentFilter.copy(kernelSize = it.roundToInt())) }
                )

                // 迭代次数滑块
                SliderControl(
                    label = "迭代次数",
                    subLabel = "重复执行 ${currentFilter.iterations} 次",
                    value = currentFilter.iterations.toFloat(),
                    range = 1f..5f,
                    steps = 3,
                    onValueChange = { onFilterChange(currentFilter.copy(iterations = it.roundToInt())) }
                )
            }
        }
    }

    @Composable
    private fun ModeOption(
        label: String,
        desc: String,
        selected: Boolean,
        onClick: () -> Unit
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp)
            )
            HelpTooltip(description = desc)
        }
    }

    @Composable
    private fun SliderControl(
        label: String,
        subLabel: String,
        value: Float,
        range: ClosedFloatingPointRange<Float>,
        steps: Int,
        onValueChange: (Float) -> Unit
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(label, style = MaterialTheme.typography.bodySmall)
                Text(subLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
            Slider(
                value = value,
                valueRange = range,
                steps = steps,
                onValueChange = onValueChange
            )
        }
    }
}