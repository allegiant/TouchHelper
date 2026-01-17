package org.eu.freex.tools.modules.image.presentation.features.filter.components.renderers

// [新增] 引入 ViewModel 和 PickingType
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.common.components.ModeSelectionRow
import org.eu.freex.tools.common.model.PickingType
import org.eu.freex.tools.modules.image.domain.model.AutoCropFilter
import org.eu.freex.tools.modules.image.domain.model.AutoCropMode
import org.eu.freex.tools.modules.image.domain.model.ImageFilter
import org.eu.freex.tools.modules.image.presentation.viewmodel.EditorCanvasViewModel
import org.koin.compose.koinInject

object AutoCropRenderer : FilterRenderer {

    @Composable
    override fun Content(
        filter: ImageFilter,
        onFilterChange: (ImageFilter) -> Unit
    ) {
        val currentFilter = filter as? AutoCropFilter ?: return

        // [修改] 注入 ViewModel
        val editorViewModel: EditorCanvasViewModel = koinInject()

        // [新增] 标记是否正在等待取色结果
        var isPickingColor by remember { mutableStateOf(false) }

        // [新增] 保持引用，供 LaunchedEffect 使用
        val currentFilterState by rememberUpdatedState(currentFilter)
        val onFilterChangeState by rememberUpdatedState(onFilterChange)

        // [新增] 监听取色事件
        LaunchedEffect(Unit) {
            editorViewModel.pickEvent.collect { event ->
                if (event is Color && isPickingColor) {
                    val picked = event
                    val hex = "#%02X%02X%02X".format(
                        (picked.red * 255).toInt(),
                        (picked.green * 255).toInt(),
                        (picked.blue * 255).toInt()
                    )
                    // 更新 Filter
                    onFilterChangeState(currentFilterState.copy(fixedColorHex = hex))
                    // 重置标记
                    isPickingColor = false
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

            // --- 模式 1: 自动探测 (默认推荐) ---
            ModeSelectionRow(
                text = "自动探测背景 (推荐)",
                description = "自动取图片角落的颜色作为背景色。通过'容差'参数可以处理背景色微小的波动 (如 JPEG 压缩噪点)。",
                selected = currentFilter.mode == AutoCropMode.AUTO_CORNERS,
                onClick = { onFilterChange(currentFilter.copy(mode = AutoCropMode.AUTO_CORNERS)) }
            )

            AnimatedVisibility(
                visible = currentFilter.mode == AutoCropMode.AUTO_CORNERS,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(start = 32.dp, bottom = 8.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 容差滑块 (Tolerance)
                    SingleValueSlider(
                        label = "颜色容差 (Fuzziness)",
                        value = currentFilter.tolerance,
                        valueRange = 0f..100f,
                        displayValue = currentFilter.tolerance.toInt().toString(),
                        onValueChange = { onFilterChange(currentFilter.copy(tolerance = it)) }
                    )

                    // 留白滑块 (Padding)
                    SingleValueSlider(
                        label = "安全留白 (Padding)",
                        value = currentFilter.padding.toFloat(),
                        valueRange = 0f..50f,
                        displayValue = "${currentFilter.padding} px",
                        onValueChange = { onFilterChange(currentFilter.copy(padding = it.toInt())) }
                    )
                }
            }

            // --- 模式 2: 指定颜色 (针对复杂情况) ---
            ModeSelectionRow(
                text = "指定背景颜色",
                description = "如果角落有干扰物，可手动指定要去除的背景颜色 hex 值。",
                selected = currentFilter.mode == AutoCropMode.FIXED_COLOR,
                onClick = { onFilterChange(currentFilter.copy(mode = AutoCropMode.FIXED_COLOR)) }
            )

            AnimatedVisibility(
                visible = currentFilter.mode == AutoCropMode.FIXED_COLOR,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(start = 32.dp, bottom = 8.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 颜色选择行
                    ColorSelectionRow(
                        hexColor = currentFilter.fixedColorHex,
                        onColorChange = { newHex ->
                            onFilterChange(currentFilter.copy(fixedColorHex = newHex))
                        },
                        onPickColor = {
                            // [修改] 触发取色流程
                            isPickingColor = true
                            editorViewModel.setPickingType(PickingType.COLOR)
                        }
                    )

                    // 同样需要留白设置
                    SingleValueSlider(
                        label = "安全留白 (Padding)",
                        value = currentFilter.padding.toFloat(),
                        valueRange = 0f..50f,
                        displayValue = "${currentFilter.padding} px",
                        onValueChange = { onFilterChange(currentFilter.copy(padding = it.toInt())) }
                    )

                    // 指定颜色模式下，通常容差也是需要的（处理不纯的背景）
                    SingleValueSlider(
                        label = "颜色容差",
                        value = currentFilter.tolerance,
                        valueRange = 0f..100f,
                        displayValue = currentFilter.tolerance.toInt().toString(),
                        onValueChange = { onFilterChange(currentFilter.copy(tolerance = it)) }
                    )
                }
            }

            // --- 通用高级设置 ---
            Text(
                "高级性能调优",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )

            // 抗噪阈值 (Noise Threshold)
            SingleValueSlider(
                label = "抗噪阈值 (连续像素)",
                value = currentFilter.noiseThreshold.toFloat(),
                valueRange = 1f..10f,
                displayValue = "${currentFilter.noiseThreshold} px",
                onValueChange = { onFilterChange(currentFilter.copy(noiseThreshold = it.toInt())) }
            )
        }
    }

    // --- 辅助组件 (保持不变) ---

    @Composable
    private fun ColorSelectionRow(
        hexColor: String,
        onColorChange: (String) -> Unit,
        onPickColor: () -> Unit
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. 颜色预览块
            val color = remember(hexColor) { parseColorSafe(hexColor) }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(color, RoundedCornerShape(4.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                    .clickable { /* 可选：点击弹出完整选色器 */ }
            )

            Spacer(modifier = Modifier.width(8.dp))

            // 2. Hex 输入框
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("#", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    BasicTextField(
                        value = hexColor.removePrefix("#"),
                        onValueChange = {
                            // 简单的输入限制
                            if (it.length <= 6) {
                                onColorChange("#$it")
                            }
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        singleLine = true
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 3. 吸管按钮
            IconButton(
                onClick = onPickColor,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Colorize,
                    contentDescription = "屏幕取色",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

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
                Text(label, style = MaterialTheme.typography.bodySmall)
                Text(
                    displayValue,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary)
            )
        }
    }

    // 简单的颜色解析安全方法
    private fun parseColorSafe(hex: String): Color {
        return try {
            val cleanHex = hex.replace("#", "")
            if (cleanHex.length == 6) {
                Color(
                    red = cleanHex.substring(0, 2).toInt(16),
                    green = cleanHex.substring(2, 4).toInt(16),
                    blue = cleanHex.substring(4, 6).toInt(16)
                )
            } else {
                Color.Transparent
            }
        } catch (e: Exception) {
            Color.Transparent
        }
    }
}