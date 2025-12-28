package org.eu.freex.tools.modules.image.presentation.ui.components.inspector

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uniffi.touch_core.ColorFilterType
import uniffi.touch_core.ImageFilter
// 引入新建的 controls 包
import org.eu.freex.tools.modules.image.presentation.ui.components.inspector.controls.*

@Composable
fun FilterControlPanel(
    currentFilter: ImageFilter,
    thresholdRange: ClosedFloatingPointRange<Float>,
    isRgbAvgEnabled: Boolean,
    onThresholdChange: (ClosedFloatingPointRange<Float>) -> Unit,
    onRgbAvgChange: (Boolean) -> Unit,
    onAddStep: () -> Unit,
    onModifyStep: () -> Unit
) {
    Column(
        modifier = Modifier
            .background(Color(0xFF1E1E1E))
            .padding(12.dp)
    ) {
        SectionHeader(title = "参数调节")

        Spacer(Modifier.height(12.dp))

        // --- 核心分发逻辑 ---
        // 根据滤镜类型，展示对应的控制面板
        when {
            // 1. 二值化 (Binarization)
            isBinarization(currentFilter) -> {
                BinarizationPanel(
                    thresholdRange = thresholdRange,
                    isRgbAvgEnabled = isRgbAvgEnabled,
                    onThresholdChange = onThresholdChange,
                    onRgbAvgChange = onRgbAvgChange
                )
            }

            // 2. 灰度 (Grayscale)
            isGrayscale(currentFilter) -> {
                GrayscalePanel(
                    isRgbAvgEnabled = isRgbAvgEnabled,
                    onRgbAvgChange = onRgbAvgChange
                )
            }

            // 3. 其他 (无参数)
            else -> {
                EmptyPanel()
            }
        }

        Spacer(Modifier.height(16.dp))

        // --- 底部通用操作按钮 ---
        ActionButtons(onModifyStep, onAddStep)
    }
}

// 辅助判断函数，使主逻辑更清晰
private fun isBinarization(filter: ImageFilter): Boolean {
    return filter is ImageFilter.Color && filter.v1 == ColorFilterType.BINARIZATION
}

private fun isGrayscale(filter: ImageFilter): Boolean {
    return filter is ImageFilter.Color && filter.v1 == ColorFilterType.GRAYSCALE
}

@Composable
private fun ActionButtons(
    onModify: () -> Unit,
    onAdd: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onModify,
            modifier = Modifier.weight(1f).height(32.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = Color(0xFF3E3E42),
                contentColor = Color.White
            ),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text("更新当前步骤", fontSize = 12.sp)
        }
        Button(
            onClick = onAdd,
            modifier = Modifier.weight(1f).height(32.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = Color(0xFF007ACC),
                contentColor = Color.White
            ),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text("添加新步骤", fontSize = 12.sp)
        }
    }
}