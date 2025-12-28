package org.eu.freex.tools.modules.image.presentation.ui.components.inspector

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import uniffi.touch_core.ImageFilter

@Composable
fun FilterSettings(
    currentFilter: ImageFilter,
    thresholdRange: ClosedFloatingPointRange<Float>,
    isRgbAvgEnabled: Boolean,
    onFilterChange: (ImageFilter) -> Unit,
    onThresholdChange: (ClosedFloatingPointRange<Float>) -> Unit,
    onRgbAvgChange: (Boolean) -> Unit,
    onAddStep: () -> Unit,
    onModifyStep: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // --- 上半部分：滤镜列表 (可滚动) ---
        FilterSelectionList(
            modifier = Modifier.weight(1f),
            currentFilter = currentFilter,
            onFilterChange = onFilterChange
        )

        Divider(color = Color(0xFF3E3E42))

        // --- 下半部分：参数调节 (固定在底部) ---
        FilterControlPanel(
            currentFilter = currentFilter,
            thresholdRange = thresholdRange,
            isRgbAvgEnabled = isRgbAvgEnabled,
            onThresholdChange = onThresholdChange,
            onRgbAvgChange = onRgbAvgChange,
            onAddStep = onAddStep,
            onModifyStep = onModifyStep
        )
    }
}