package org.eu.freex.tools.modules.image.presentation.ui.components.inspector.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BinarizationPanel(
    thresholdRange: ClosedFloatingPointRange<Float>,
    isRgbAvgEnabled: Boolean,
    onThresholdChange: (ClosedFloatingPointRange<Float>) -> Unit,
    onRgbAvgChange: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 二值化需要：阈值控制
        ThresholdControl(
            range = thresholdRange,
            onValueChange = onThresholdChange
        )

        // 二值化也支持：RGB 平均值算法
        RgbAvgControl(
            isEnabled = isRgbAvgEnabled,
            onChange = onRgbAvgChange
        )
    }
}