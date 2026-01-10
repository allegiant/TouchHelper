package org.eu.freex.tools.modules.image.presentation.features.segmentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
// 请确保引用路径正确
import org.eu.freex.tools.modules.image.domain.model.SegmentationConfig
import org.eu.freex.tools.modules.image.domain.model.SegmentationMode
import org.eu.freex.tools.modules.image.domain.model.SegmentationRect
import org.eu.freex.tools.modules.image.presentation.core.SegmentationInteraction
import java.awt.image.BufferedImage

@Composable
fun SegmentationPanel(
    modifier: Modifier = Modifier,
    config: SegmentationConfig,
    results: List<SegmentationRect>,
    labels: Map<Int, String>,
    interaction: SegmentationInteraction,
    sourceImage: BufferedImage?,
    onConfigChange: (SegmentationConfig) -> Unit,
    onSelectChar: (Int) -> Unit,
    onSubmitLabel: (String) -> Unit,
    onStopLabeling: () -> Unit
) {
    // [性能关键] 缓存 ImageBitmap，避免每次重绘都转换
    val composeBitmap = remember(sourceImage) { sourceImage?.toComposeImageBitmap() }

    Column(modifier = modifier.padding(8.dp)) {
        ConfigSection(config, onConfigChange)

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

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

        // 标注弹窗
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

// ==========================================
// 🚀 核心优化区域：列表性能优化 v2
// ==========================================

@Composable
fun CharGridSection(
    results: List<SegmentationRect>,
    labels: Map<Int, String>,
    selectedIndex: Int,
    sourceImage: ImageBitmap?,
    onSelectChar: (Int) -> Unit
) {
    val gridState = rememberLazyGridState()

    // 自动滚动到选中项
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
        itemsIndexed(
            items = results,
            // [优化1] 必须加 key！防止列表重排
            key = { index, _ -> index }
        ) { index, rect ->
            // [优化2] 使用 Canvas 合并绘制的 Item，减少 Modifier 层级
            CharGridItemUnified(
                index = index,
                rect = rect,
                label = labels[index],
                isSelected = index == selectedIndex,
                sourceImage = sourceImage,
                onClick = { onSelectChar(index) }
            )
        }
    }
}

/**
 * 🎨 统一绘制组件 (Canvas-Only)
 * 移除了 Box, background, border, clip 等所有 Modifier，全部在 Canvas 内一次性画完。
 * 这是 Compose 中性能最高的绘制方式。
 */
@Composable
private fun CharGridItemUnified(
    index: Int,
    rect: SegmentationRect,
    label: String?,
    isSelected: Boolean,
    sourceImage: ImageBitmap?,
    onClick: () -> Unit
) {
    // 预取颜色
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainerLow
    val labelBgColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)

    // 布局容器：Box 仅用于提供尺寸和点击事件，不参与绘制
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            // 点击事件放在这里
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cornerRadius = 4.dp.toPx() // 圆角半径

            // 1. 绘制背景 (替代 .background)
            val bgColor = if (isSelected) primaryContainer else surfaceContainer
            drawRoundRect(
                color = bgColor,
                cornerRadius = CornerRadius(cornerRadius, cornerRadius)
            )

            // 2. 绘制图片切片 (替代 VirtualSliceCanvas)
            if (sourceImage != null) {
                // 计算内缩 (Padding)
                val padding = 3.dp.toPx()
                val imgDstW = w - padding * 2
                val imgDstH = h - padding * 2

                // 简单的居中计算
                if (imgDstW > 0 && imgDstH > 0) {
                    val srcOffset = IntOffset(rect.left, rect.top)
                    val srcSize = IntSize(rect.width.toInt(), rect.height.toInt())

                    drawImage(
                        image = sourceImage,
                        srcOffset = srcOffset,
                        srcSize = srcSize,
                        dstOffset = IntOffset(padding.toInt(), padding.toInt()),
                        dstSize = IntSize(imgDstW.toInt(), imgDstH.toInt()),
                        // [关键] 必须使用 None，双线性插值(Low/Medium)在小图缩放时极慢
                        filterQuality = FilterQuality.None
                    )
                }
            }

            // 3. 绘制边框 (替代 .border)
            val borderColor = if (isSelected) primaryColor else outlineColor
            val borderWidth = if (isSelected) 2.dp.toPx() else 1.dp.toPx()

            // 边框居中绘制，需要偏移半个边框宽度
            drawRoundRect(
                color = borderColor,
                cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                style = Stroke(width = borderWidth)
            )
        }

        // 4. 文字标签 (仅文字使用 Composable，因为它在 Canvas 里很难画)
        if (!label.isNullOrEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(labelBgColor)
                    .padding(vertical = 2.dp)
            )
        }
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

// 简单的 Canvas 包装器，用于弹窗内复用
@Composable
fun SimpleSliceCanvas(image: ImageBitmap, rect: SegmentationRect, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawImage(
            image = image,
            srcOffset = IntOffset(rect.left, rect.top),
            srcSize = IntSize(rect.width.toInt(), rect.height.toInt()),
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            filterQuality = FilterQuality.None
        )
    }
}

@Composable
fun LabelingDialog(
    rect: SegmentationRect,
    sourceImage: ImageBitmap,
    initialText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember(initialText) { mutableStateOf(initialText) }

    Dialog(onDismissRequest = onDismiss) {
        // 弹窗这种低频界面用 Card 没问题
        Card(modifier = Modifier.padding(16.dp)) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(modifier = Modifier.size(120.dp).border(1.dp, Color.Gray)) {
                    SimpleSliceCanvas(sourceImage, rect, Modifier.fillMaxSize())
                }
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("输入字符") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = { onConfirm(text) }) {
                    Text("确认 (Enter)")
                }
            }
        }
    }
}