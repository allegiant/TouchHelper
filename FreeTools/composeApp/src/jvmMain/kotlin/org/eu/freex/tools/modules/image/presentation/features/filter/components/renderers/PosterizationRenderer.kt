package org.eu.freex.tools.modules.image.presentation.features.filter.components.renderers

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.modules.image.domain.model.ImageFilter
import org.eu.freex.tools.modules.image.domain.model.PosterizationFilter
import org.eu.freex.tools.modules.image.domain.model.PosterizationMode
import kotlin.math.roundToInt

object PosterizationRenderer : FilterRenderer {

    @Composable
    override fun Content(filter: ImageFilter, onFilterChange: (ImageFilter) -> Unit) {
        val current = filter as? PosterizationFilter ?: return

        // 根据模式决定显示的标签
        val labels = if (current.mode == PosterizationMode.RGB) {
            listOf("红色 (R)", "绿色 (G)", "蓝色 (B)")
        } else {
            listOf("色相 (H)", "饱和度 (S)", "亮度 (V)")
        }
        val helpText = if (current.mode == PosterizationMode.RGB) {
            "RGB适合纯色背景。勾选2个进行差分 (如 |R-G|)。"
        } else {
            "HSV抗光照干扰强。提取H可无视阴影；提取S可找鲜艳物体。"
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(
                        selected = current.mode == PosterizationMode.RGB,
                        onClick = { onFilterChange(current.copy(mode = PosterizationMode.RGB)) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text("RGB 模式") }

                    SegmentedButton(
                        selected = current.mode == PosterizationMode.HSV,
                        onClick = { onFilterChange(current.copy(mode = PosterizationMode.HSV)) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text("HSV 模式 (推荐)") }
                }
            }
            // --- 区域 1: 彩色多值化 ---
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = current.isMultiValue,
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    // 互斥：开启多值化，关闭通道提取
                                    onFilterChange(
                                        current.copy(
                                            isMultiValue = true,
                                            channel1 = false,
                                            channel2 = false,
                                            channel3 = false
                                        )
                                    )
                                } else {
                                    onFilterChange(current.copy(isMultiValue = false))
                                }
                            }
                        )
                        Text("彩色多值化", style = MaterialTheme.typography.titleSmall)
                    }

                    if (current.isMultiValue) {
                        Text("色阶: ${current.level}", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = current.level.toFloat(),
                            onValueChange = { onFilterChange(current.copy(level = it.roundToInt())) },
                            valueRange = 2f..255f,
                            steps = 253
                        )
                    }
                }
            }

            // --- 区域 2: 通道提取 ---
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("通道提取", style = MaterialTheme.typography.titleSmall)
                    Text(
                        helpText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                        ChannelCheckbox(labels[0], current.channel1) {
                            handleChannelCheck(current, 1, it, onFilterChange)
                        }
                        ChannelCheckbox(labels[1], current.channel2) {
                            handleChannelCheck(current, 2, it, onFilterChange)
                        }
                        ChannelCheckbox(labels[2], current.channel3) {
                            handleChannelCheck(current, 3, it, onFilterChange)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ChannelCheckbox(label: String, checked: Boolean, onCheck: (Boolean) -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = onCheck)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }

    private fun handleChannelCheck(
        current: PosterizationFilter,
        index: Int,
        isChecked: Boolean,
        onUpdate: (PosterizationFilter) -> Unit
    ) {
        var newState = current.copy(isMultiValue = false)
        newState = when (index) {
            1 -> newState.copy(channel1 = isChecked)
            2 -> newState.copy(channel2 = isChecked)
            3 -> newState.copy(channel3 = isChecked)
            else -> newState
        }

        // 3. 数量限制：如果 > 2，执行“挤出”逻辑
        val count = listOf(newState.channel1, newState.channel2, newState.channel3).count { it }
        if (count > 2) {
            if (index == 1) newState = newState.copy(channel1 = false) // 选R挤掉G (示例策略)
            else if (index == 2) newState = newState.copy(channel2 = false)
            else if (index == 3) newState = newState.copy(channel3 = false)
        }

        onUpdate(newState)
    }
}