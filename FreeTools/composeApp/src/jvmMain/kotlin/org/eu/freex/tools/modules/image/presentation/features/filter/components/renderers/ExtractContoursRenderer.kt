package org.eu.freex.tools.modules.image.presentation.features.filter.components.renderers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.common.components.HelpTooltip
import org.eu.freex.tools.modules.image.domain.model.ExtractContoursFilter
import org.eu.freex.tools.modules.image.domain.model.ImageFilter

object ExtractContoursRenderer : FilterRenderer {

    @Composable
    override fun Content(
        filter: ImageFilter,
        onFilterChange: (ImageFilter) -> Unit
    ) {
        val current = filter as? ExtractContoursFilter ?: return

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

            // --- 模式 1: Canny 边缘检测 (推荐) ---
            ModeRow(
                text = "Canny 边缘检测 (推荐)",
                description = "经典的边缘检测算法。生成的线条极细（单像素），位置精准，适合提取 UI 边框、表格线。",
                selected = current.isCanny,
                onClick = { onFilterChange(current.copy(isCanny = true)) }
            )

            AnimatedVisibility(
                visible = current.isCanny,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(start = 32.dp, bottom = 8.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Canny 算法有两个阈值：低阈值和高阈值
                    // 这里我们为了交互简单，拆成两个滑块，但你可以根据需要做成 RangeSlider

                    ControlSlider(
                        label = "强边缘阈值 (High)",
                        value = current.cannyHigh,
                        range = 10f..400f,
                        onValueChange = { onFilterChange(current.copy(cannyHigh = it)) }
                    )

                    ControlSlider(
                        label = "弱边缘阈值 (Low)",
                        value = current.cannyLow,
                        range = 5f..200f,
                        onValueChange = { onFilterChange(current.copy(cannyLow = it)) }
                    )
                }
            }

            // --- 模式 2: 形态学梯度 ---
            ModeRow(
                text = "形态学轮廓 (空心字)",
                description = "通过膨胀减去腐蚀得到的轮廓。线条较粗，适合把实心汉字转换为空心字，便于做文字匹配。",
                selected = !current.isCanny,
                onClick = { onFilterChange(current.copy(isCanny = false)) }
            )

            AnimatedVisibility(
                visible = !current.isCanny,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(start = 32.dp, bottom = 8.dp).fillMaxWidth(),
                ) {
                    ControlSlider(
                        label = "轮廓粗细 (核大小)",
                        value = current.morphKernel.toFloat(),
                        range = 1f..10f,
                        displayValue = "${current.morphKernel} px",
                        onValueChange = { onFilterChange(current.copy(morphKernel = it.toInt())) }
                    )
                }
            }
        }
    }

    @Composable
    private fun ModeRow(
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
    private fun ControlSlider(
        label: String,
        value: Float,
        range: ClosedFloatingPointRange<Float>,
        displayValue: String = String.format("%.0f", value),
        onValueChange: (Float) -> Unit
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(label, style = MaterialTheme.typography.bodySmall)
                Text(displayValue, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = range,
                colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary)
            )
        }
    }
}