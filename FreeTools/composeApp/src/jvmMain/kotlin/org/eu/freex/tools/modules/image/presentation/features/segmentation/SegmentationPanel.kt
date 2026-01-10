package org.eu.freex.tools.modules.image.presentation.features.segmentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
// [关键] 引入 Domain 模型
import org.eu.freex.tools.modules.image.domain.model.SegmentationConfig
import org.eu.freex.tools.modules.image.domain.model.SegmentationMode
import org.eu.freex.tools.modules.image.domain.model.SegmentationRect
// [关键] 引入 UI 交互状态
import org.eu.freex.tools.modules.image.presentation.core.SegmentationInteraction
import java.awt.image.BufferedImage

@Composable
fun SegmentationPanel(
    modifier: Modifier = Modifier,
    // 数据源 (来自 Domain / Workspace)
    config: SegmentationConfig,
    results: List<SegmentationRect>,
    labels: Map<Int, String>,
    // 交互状态 (来自 UI State)
    interaction: SegmentationInteraction,
    // 图像源
    sourceImage: BufferedImage?,
    // 回调
    onConfigChange: (SegmentationConfig) -> Unit,
    onSelectChar: (Int) -> Unit,
    onSubmitLabel: (String) -> Unit,
    onStopLabeling: () -> Unit
) {
    // 将 AWT BufferedImage 转换为 Compose ImageBitmap 用于渲染
    // 使用 remember 缓存转换结果，避免重绘时重复转换
    val composeBitmap = remember(sourceImage) { sourceImage?.toComposeImageBitmap() }

    Column(modifier = modifier.padding(8.dp)) {
        // 1. 配置区域
        ConfigSection(config, onConfigChange)

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // 2. 结果网格
        if (results.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无切割结果", style = MaterialTheme.typography.bodySmall)
            }
        } else {
            CharGridSection(
                results = results,
                labels = labels,
                selectedIndex = interaction.selectedIndex,
                sourceImage = composeBitmap,
                onSelectChar = onSelectChar
            )
        }

        // 3. 标注弹窗 (浮层)
        if (interaction.isLabeling && interaction.selectedIndex in results.indices && composeBitmap != null) {
            val rect = results[interaction.selectedIndex]
            val initialText = labels[interaction.selectedIndex] ?: ""

            LabelingDialog(
                rect = rect,
                sourceImage = composeBitmap,
                initialText = initialText,
                onConfirm = onSubmitLabel,
                onDismiss = onStopLabeling
            )
        }
    }
}

@Composable
fun ConfigSection(config: SegmentationConfig, onChange: (SegmentationConfig) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        // 模式选择
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text("模式: ${config.mode.name}")
                Icon(Icons.Default.ArrowDropDown, null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                SegmentationMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.name) },
                        onClick = {
                            onChange(config.copy(mode = mode))
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 参数面板
        when (config.mode) {
            SegmentationMode.FIXED_GRID -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactNumInput("行", config.rowCount.toInt()) { onChange(config.copy(rowCount = it.toUInt())) }
                    CompactNumInput("列", config.colCount.toInt()) { onChange(config.copy(colCount = it.toUInt())) }
                }
            }
            SegmentationMode.PROJECTION -> {
                Text("阈值: ${config.projectionThreshold}", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = config.projectionThreshold.toFloat(),
                    onValueChange = { onChange(config.copy(projectionThreshold = it.toInt().toUByte())) },
                    valueRange = 0f..255f
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = config.splitRows, onCheckedChange = { onChange(config.copy(splitRows = it)) })
                    Text("切行", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.width(16.dp))
                    Checkbox(checked = config.splitCols, onCheckedChange = { onChange(config.copy(splitCols = it)) })
                    Text("切列", style = MaterialTheme.typography.bodySmall)
                }
            }
            SegmentationMode.CONNECTED_COMP -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactNumInput("最小宽", config.minWidth.toInt()) { onChange(config.copy(minWidth = it.toUInt())) }
                    CompactNumInput("最小高", config.minHeight.toInt()) { onChange(config.copy(minHeight = it.toUInt())) }
                }
            }
        }
    }
}

@Composable
fun CharGridSection(
    results: List<SegmentationRect>,
    labels: Map<Int, String>,
    selectedIndex: Int,
    sourceImage: ImageBitmap?,
    onSelectChar: (Int) -> Unit
) {
    val gridState = rememberLazyGridState()

    // 自动跟随游标滚动
    LaunchedEffect(selectedIndex) {
        if (selectedIndex != -1) {
            gridState.animateScrollToItem(selectedIndex)
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 48.dp),
        state = gridState,
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(results) { index, rect ->
            val isSelected = index == selectedIndex
            val label = labels[index]

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                ),
                border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier
                    .aspectRatio(1f)
                    .clickable { onSelectChar(index) }
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    // 虚拟切片渲染：只绘制 Rect 区域
                    if (sourceImage != null) {
                        VirtualSliceCanvas(sourceImage, rect, modifier = Modifier.padding(2.dp))
                    }
                    // 标签显示
                    if (!label.isNullOrEmpty()) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                                .fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/**
 * 虚拟切片画布：核心性能组件
 * 不创建新的 Bitmap，直接从大图中绘制指定区域
 */
@Composable
fun VirtualSliceCanvas(image: ImageBitmap, rect: SegmentationRect, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val srcOffset = IntOffset(rect.left, rect.top)
        // Rect 中的 width/height 是 UInt，需要转 Int
        val srcSize = IntSize(rect.width.toInt(), rect.height.toInt())
        val dstSize = IntSize(size.width.toInt(), size.height.toInt())

        // 简单绘制，填满格子
        drawImage(
            image = image,
            srcOffset = srcOffset,
            srcSize = srcSize,
            dstOffset = IntOffset.Zero,
            dstSize = dstSize
        )
    }
}

@Composable
fun RowScope.CompactNumInput(label: String, value: Int, onValueChange: (Int) -> Unit) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { it.toIntOrNull()?.let(onValueChange) },
        label = { Text(label) },
        modifier = Modifier.weight(1f),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall
    )
}

@Composable
fun LabelingDialog(
    rect: SegmentationRect,
    sourceImage: ImageBitmap,
    initialText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // 每次 initialText 变化时（比如按回车切到下一个字），重置输入框内容
    var text by remember(initialText) { mutableStateOf(initialText) }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.padding(16.dp)) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 放大预览
                Box(modifier = Modifier.size(120.dp).border(1.dp, Color.Gray)) {
                    VirtualSliceCanvas(sourceImage, rect)
                }
                Spacer(modifier = Modifier.height(16.dp))

                // 输入框
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("输入字符") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))

                // 确认按钮
                Button(onClick = { onConfirm(text) }) {
                    Text("确认 (Enter)")
                }
            }
        }
    }
}