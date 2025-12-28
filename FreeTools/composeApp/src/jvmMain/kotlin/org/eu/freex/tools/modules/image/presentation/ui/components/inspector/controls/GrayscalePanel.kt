package org.eu.freex.tools.modules.image.presentation.ui.components.inspector.controls

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable

@Composable
fun GrayscalePanel(
    isRgbAvgEnabled: Boolean,
    onRgbAvgChange: (Boolean) -> Unit
) {
    Column {
        // 灰度处理只需要：RGB 平均值算法选项
        RgbAvgControl(
            isEnabled = isRgbAvgEnabled,
            onChange = onRgbAvgChange
        )
    }
}