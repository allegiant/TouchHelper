package org.eu.freex.tools.modules.image.presentation.features.segmentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.common.components.CompactNumericInput
import org.eu.freex.tools.common.components.FCheckBox
import org.eu.freex.tools.common.components.ModeSelectionRow
import org.eu.freex.tools.modules.image.domain.model.SegmentationConfig
import org.eu.freex.tools.modules.image.domain.model.SegmentationMode

@Composable
fun SegmentationConfigSection(
    config: SegmentationConfig,
    onChange: (SegmentationConfig) -> Unit,
    onPickPoint: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

        // --- 模式 1: 固定网格 ---
        ModeSelectionRow(
            text = "固定网格切分",
            description = "指定起点、大小和行列数进行网格切割。",
            selected = config.mode == SegmentationMode.FIXED_GRID,
            onClick = { onChange(config.copy(mode = SegmentationMode.FIXED_GRID)) }
        )

        AnimatedVisibility(
            visible = config.mode == SegmentationMode.FIXED_GRID,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier.padding(start = 32.dp, bottom = 8.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. 起点位置 + 坐标拾取
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        CompactNumericInput(
                            label = "起点 X",
                            value = config.startX.toUInt(),
                            onValueChange = { v -> v?.let { onChange(config.copy(startX = it.toInt())) } }
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        CompactNumericInput(
                            label = "起点 Y",
                            value = config.startY.toUInt(),
                            onValueChange = { v -> v?.let { onChange(config.copy(startY = it.toInt())) } }
                        )
                    }
                    IconButton(
                        onClick = onPickPoint,
                        modifier = Modifier.size(32.dp),
                        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = "拾取坐标")
                    }
                }

                // 2. 切割大小
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        CompactNumericInput(
                            label = "宽 (W)",
                            value = config.cellWidth,
                            onValueChange = { it?.let { v -> onChange(config.copy(cellWidth = v)) } }
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        CompactNumericInput(
                            label = "高 (H)",
                            value = config.cellHeight,
                            onValueChange = { it?.let { v -> onChange(config.copy(cellHeight = v)) } }
                        )
                    }
                }

                // 3. 间距
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        CompactNumericInput(
                            label = "列间隙",
                            value = config.colGap.toUInt(),
                            onValueChange = { v -> v?.let { onChange(config.copy(colGap = it.toInt())) } }
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        CompactNumericInput(
                            label = "行间隙",
                            value = config.rowGap.toUInt(),
                            onValueChange = { v -> v?.let { onChange(config.copy(rowGap = it.toInt())) } }
                        )
                    }
                }

                // 4. 行列数量
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        CompactNumericInput(
                            label = "列数 (Cols)",
                            value = config.colCount,
                            onValueChange = { it?.let { v -> onChange(config.copy(colCount = v)) } }
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        CompactNumericInput(
                            label = "行数 (Rows)",
                            value = config.rowCount,
                            onValueChange = { it?.let { v -> onChange(config.copy(rowCount = v)) } }
                        )
                    }
                }
            }
        }

        // --- 模式 2: 投影切割 ---
        ModeSelectionRow(
            text = "投影切分 (X/Y轴)",
            description = "分析图像在水平和垂直方向的像素投影，自动识别空白间隙进行切割。",
            selected = config.mode == SegmentationMode.PROJECTION,
            onClick = { onChange(config.copy(mode = SegmentationMode.PROJECTION)) }
        )

        AnimatedVisibility(
            visible = config.mode == SegmentationMode.PROJECTION,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier.padding(start = 32.dp, bottom = 8.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("分割阈值: ${config.projectionThreshold}", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.width(8.dp))
                    Slider(
                        value = config.projectionThreshold.toFloat(),
                        onValueChange = { onChange(config.copy(projectionThreshold = it.toInt().toUByte())) },
                        valueRange = 0f..255f,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f)) {
                        FCheckBox(
                            text = "水平切行",
                            isEnabled = config.splitRows,
                            onChange = { onChange(config.copy(splitRows = it)) }
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        FCheckBox(
                            text = "垂直切列",
                            isEnabled = config.splitCols,
                            onChange = { onChange(config.copy(splitCols = it)) }
                        )
                    }
                }
            }
        }

        // --- 模式 3: 连通域 (Updated) ---
        ModeSelectionRow(
            text = "连通域切分 (Blob)",
            description = "自动提取连通块，支持合并邻近碎片以识别完整目标。",
            selected = config.mode == SegmentationMode.CONNECTED_COMP,
            onClick = { onChange(config.copy(mode = SegmentationMode.CONNECTED_COMP)) }
        )

        AnimatedVisibility(
            visible = config.mode == SegmentationMode.CONNECTED_COMP,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier.padding(start = 32.dp, bottom = 8.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 第一行：宽度限制
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        CompactNumericInput(
                            label = "最小宽度",
                            value = config.minWidth,
                            onValueChange = { it?.let { v -> onChange(config.copy(minWidth = v)) } },
                            unit = "px"
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        CompactNumericInput(
                            label = "最大宽度 (0不限)",
                            value = config.maxWidth,
                            onValueChange = { it?.let { v -> onChange(config.copy(maxWidth = v)) } },
                            unit = "px"
                        )
                    }
                }

                // 第二行：高度限制
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        CompactNumericInput(
                            label = "最小高度",
                            value = config.minHeight,
                            onValueChange = { it?.let { v -> onChange(config.copy(minHeight = v)) } },
                            unit = "px"
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        CompactNumericInput(
                            label = "最大高度 (0不限)",
                            value = config.maxHeight,
                            onValueChange = { it?.let { v -> onChange(config.copy(maxHeight = v)) } },
                            unit = "px"
                        )
                    }
                }

                // 第三行：合并策略
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        CompactNumericInput(
                            label = "合并距离",
                            value = config.mergeDistance,
                            onValueChange = { it?.let { v -> onChange(config.copy(mergeDistance = v)) } },
                            unit = "px"
                        )
                    }
                    // 占位，保持排版对齐
                    Box(modifier = Modifier.weight(1f)) {
                        Text(
                            "碎片间距小于此值将被合并",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.align(Alignment.CenterStart)
                        )
                    }
                }
            }
        }
    }
}