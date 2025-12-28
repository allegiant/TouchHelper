package org.eu.freex.tools.modules.image.presentation.ui.components.inspector.controls

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.RangeSlider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

/**
 * 阈值调节滑块组件
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ThresholdControl(
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("阈值范围", color = Color.Gray, fontSize = 12.sp)
            Text(
                "${range.start.toInt()} - ${range.endInclusive.toInt()}",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
        RangeSlider(
            value = range,
            onValueChange = onValueChange,
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF007ACC),
                activeTrackColor = Color(0xFF007ACC),
                inactiveTrackColor = Color(0xFF3E3E42)
            )
        )
    }
}

/**
 * RGB 平均值开关组件
 */
@Composable
fun RgbAvgControl(
    isEnabled: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { onChange(!isEnabled) }
    ) {
        Checkbox(
            checked = isEnabled,
            onCheckedChange = onChange,
            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF007ACC))
        )
        Text("使用 RGB 平均值", color = Color.LightGray, fontSize = 12.sp)
    }
}