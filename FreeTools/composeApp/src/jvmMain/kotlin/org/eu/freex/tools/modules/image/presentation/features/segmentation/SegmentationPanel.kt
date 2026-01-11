package org.eu.freex.tools.modules.image.presentation.features.segmentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage

// 引入共享组件
import org.eu.freex.tools.common.components.ModeSelectionRow
import org.eu.freex.tools.common.components.CompactNumericInput
import org.eu.freex.tools.common.components.FCheckBox

import org.eu.freex.tools.modules.image.domain.model.SegmentationConfig
import org.eu.freex.tools.modules.image.domain.model.SegmentationMode
import org.eu.freex.tools.modules.image.domain.model.SegmentationRect
import org.eu.freex.tools.modules.image.presentation.core.SegmentationInteraction

/**
 * 🎨 性能优化：缓存颜色配置
 */
@Immutable
data class GridItemColors(
    val selectedBg: Color,
    val unselectedBg: Color,
    val selectedBorder: Color,
    val unselectedBorder: Color,
    val labelBg: Color,
    val labelText: Color
)

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
    val bigComposeBitmap = remember(sourceImage) { sourceImage?.toComposeImageBitmap() }
    val slicedCache = remember { mutableStateListOf<ImageBitmap?>() }

    LaunchedEffect(results, sourceImage) {
        if (sourceImage == null || results.isEmpty()) {
            slicedCache.clear()
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            val newSlices = ArrayList<ImageBitmap?>(results.size)
            for (rect in results) {
                try {
                    val rW = rect.width.toInt()
                    val rH = rect.height.toInt()
                    if (rW > 0 && rH > 0 &&
                        rect.left >= 0 && rect.top >= 0 &&
                        (rect.left + rW) <= sourceImage.width &&
                        (rect.top + rH) <= sourceImage.height
                    ) {
                        val subView = sourceImage.getSubimage(rect.left, rect.top, rW, rH)
                        val copy = BufferedImage(rW, rH, BufferedImage.TYPE_INT_ARGB)
                        val g = copy.createGraphics()
                        g.drawImage(subView, 0, 0, null)
                        g.dispose()
                        newSlices.add(copy.toComposeImageBitmap())
                    } else {
                        newSlices.add(null)
                    }
                } catch (e: Exception) {
                    newSlices.add(null)
                }
            }
            withContext(Dispatchers.Main) {
                slicedCache.clear()
                slicedCache.addAll(newSlices)
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(8.dp)) {
        // 1. 配置区域 (支持少量滚动，防止屏幕过矮时遮挡)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            ConfigSection(config, onConfigChange)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // 2. 字模列表区域 (独立，占据剩余空间)
        if (results.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("暂无切割结果，请调整上方参数", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
        } else {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                CharGridSection(
                    results = results,
                    labels = labels,
                    selectedIndex = interaction.selectedIndex,
                    sourceImage = bigComposeBitmap,
                    slicedImages = slicedCache,
                    onSelectChar = onSelectChar
                )
            }

            Text(
                text = "共切割出 ${results.size} 个字符",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 4.dp).align(Alignment.End)
            )
        }

        if (interaction.isLabeling && interaction.selectedIndex in results.indices && bigComposeBitmap != null) {
            LabelingDialog(
                rect = results[interaction.selectedIndex],
                sourceImage = bigComposeBitmap,
                initialText = labels[interaction.selectedIndex] ?: "",
                onConfirm = onSubmitLabel,
                onDismiss = onStopLabeling
            )
        }
    }
}

@Composable
fun ConfigSection(config: SegmentationConfig, onChange: (SegmentationConfig) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

        // --- 模式 1: 固定网格 ---
        ModeSelectionRow(
            text = "固定网格切分",
            description = "按指定的行数和列数，将图像均匀切割成网格。适合排列极其规整的字模图。",
            selected = config.mode == SegmentationMode.FIXED_GRID,
            onClick = { onChange(config.copy(mode = SegmentationMode.FIXED_GRID)) }
        )

        AnimatedVisibility(
            visible = config.mode == SegmentationMode.FIXED_GRID,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Row(
                modifier = Modifier.padding(start = 32.dp, bottom = 8.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 使用 SharedComponents 中的 CompactNumericInput
                Box(modifier = Modifier.weight(1f)) {
                    CompactNumericInput(
                        label = "行数",
                        value = config.rowCount,
                        onValueChange = { it?.let { v -> onChange(config.copy(rowCount = v)) } }
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    CompactNumericInput(
                        label = "列数",
                        value = config.colCount,
                        onValueChange = { it?.let { v -> onChange(config.copy(colCount = v)) } }
                    )
                }
            }
        }

        // --- 模式 2: 投影切割 ---
        ModeSelectionRow(
            text = "投影切分 (X/Y轴)",
            description = "分析图像在水平和垂直方向的像素投影，自动识别空白间隙进行切割。适合绝大多数排列整齐的文档或屏幕截图。",
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

        // --- 模式 3: 连通域 ---
        ModeSelectionRow(
            text = "连通域切分 (Blob)",
            description = "基于像素的连通性分析，自动提取独立的文字或图形块。适合排列不规则、散乱的字符，或者投影法无法分割的粘连字符。",
            selected = config.mode == SegmentationMode.CONNECTED_COMP,
            onClick = { onChange(config.copy(mode = SegmentationMode.CONNECTED_COMP)) }
        )

        AnimatedVisibility(
            visible = config.mode == SegmentationMode.CONNECTED_COMP,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Row(
                modifier = Modifier.padding(start = 32.dp, bottom = 8.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
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
                        label = "最小高度",
                        value = config.minHeight,
                        onValueChange = { it?.let { v -> onChange(config.copy(minHeight = v)) } },
                        unit = "px"
                    )
                }
            }
        }
    }
}

// ==========================================
// 🚀 核心优化区域 (CharGridSection, CharGridItemUnified) 保持不变
// ==========================================

@Composable
fun CharGridSection(
    results: List<SegmentationRect>,
    labels: Map<Int, String>,
    selectedIndex: Int,
    sourceImage: ImageBitmap?,
    slicedImages: List<ImageBitmap?>,
    onSelectChar: (Int) -> Unit
) {
    val gridState = rememberLazyGridState()
    val textMeasurer = rememberTextMeasurer()

    LaunchedEffect(selectedIndex) {
        if (selectedIndex != -1) {
            gridState.animateScrollToItem(selectedIndex)
        }
    }

    val colorScheme = MaterialTheme.colorScheme
    val gridColors = remember(colorScheme) {
        GridItemColors(
            selectedBg = colorScheme.primaryContainer,
            unselectedBg = colorScheme.surfaceContainerLow,
            selectedBorder = colorScheme.primary,
            unselectedBorder = colorScheme.outlineVariant,
            labelBg = colorScheme.surface.copy(alpha = 0.75f),
            labelText = colorScheme.onSurface
        )
    }

    val labelTextStyle = MaterialTheme.typography.labelSmall.copy(
        color = gridColors.labelText,
        fontSize = 10.sp,
        textAlign = TextAlign.Center
    )

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 48.dp),
        state = gridState,
        contentPadding = PaddingValues(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(
            items = results,
            key = { index, _ -> index },
            contentType = { _, _ -> 0 }
        ) { index, rect ->
            val cachedSlice = if (index < slicedImages.size) slicedImages[index] else null

            CharGridItemUnified(
                index = index,
                rect = rect,
                label = labels[index],
                isSelected = index == selectedIndex,
                sourceImage = sourceImage,
                cachedSlice = cachedSlice,
                colors = gridColors,
                textMeasurer = textMeasurer,
                textStyle = labelTextStyle,
                onItemClick = onSelectChar
            )
        }
    }
}

@Composable
private fun CharGridItemUnified(
    index: Int,
    rect: SegmentationRect,
    label: String?,
    isSelected: Boolean,
    sourceImage: ImageBitmap?,
    cachedSlice: ImageBitmap?,
    colors: GridItemColors,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    textStyle: TextStyle,
    onItemClick: (Int) -> Unit
) {
    Spacer(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable { onItemClick(index) }
            .drawWithCache {
                val cornerRadiusPx = 4.dp.toPx()
                val paddingPx = 3.dp.toPx()

                val rWidth = rect.width.toInt()
                val rHeight = rect.height.toInt()
                val rLeft = rect.left
                val rTop = rect.top

                val textResult = if (!label.isNullOrEmpty()) {
                    textMeasurer.measure(
                        text = label,
                        style = textStyle,
                        maxLines = 1,
                        constraints = Constraints(maxWidth = size.width.toInt())
                    )
                } else null

                onDrawBehind {
                    val w = size.width
                    val h = size.height
                    val bgColor = if (isSelected) colors.selectedBg else colors.unselectedBg

                    // 1. 背景
                    drawRoundRect(
                        color = bgColor,
                        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                    )

                    // 2. 图片 (混合渲染)
                    val imgDstW = w - paddingPx * 2
                    val imgDstH = h - paddingPx * 2

                    if (imgDstW > 0 && imgDstH > 0) {
                        if (cachedSlice != null) {
                            drawImage(
                                image = cachedSlice,
                                dstOffset = IntOffset(paddingPx.toInt(), paddingPx.toInt()),
                                dstSize = IntSize(imgDstW.toInt(), imgDstH.toInt()),
                                filterQuality = FilterQuality.None
                            )
                        } else if (sourceImage != null) {
                            val imgW = sourceImage.width
                            val imgH = sourceImage.height
                            val safeLeft = rLeft.coerceAtLeast(0)
                            val safeTop = rTop.coerceAtLeast(0)
                            val safeRight = (rLeft + rWidth).coerceAtMost(imgW)
                            val safeBottom = (rTop + rHeight).coerceAtMost(imgH)
                            val srcW = safeRight - safeLeft
                            val srcH = safeBottom - safeTop

                            if (srcW > 0 && srcH > 0) {
                                drawImage(
                                    image = sourceImage,
                                    srcOffset = IntOffset(safeLeft, safeTop),
                                    srcSize = IntSize(srcW, srcH),
                                    dstOffset = IntOffset(paddingPx.toInt(), paddingPx.toInt()),
                                    dstSize = IntSize(imgDstW.toInt(), imgDstH.toInt()),
                                    filterQuality = FilterQuality.None
                                )
                            }
                        }
                    }

                    // 3. 边框
                    val borderColor = if (isSelected) colors.selectedBorder else colors.unselectedBorder
                    val borderWidth = if (isSelected) 2.dp.toPx() else 1.dp.toPx()
                    drawRoundRect(
                        color = borderColor,
                        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                        style = Stroke(width = borderWidth)
                    )

                    // 4. 文字
                    if (textResult != null) {
                        val labelH = textResult.size.height + 4.dp.toPx()
                        drawRect(
                            color = colors.labelBg,
                            topLeft = Offset(0f, h - labelH),
                            size = Size(w, labelH)
                        )
                        val textOffsetX = (w - textResult.size.width) / 2
                        val textOffsetY = h - labelH + 2.dp.toPx()
                        translate(left = textOffsetX, top = textOffsetY) {
                            drawText(textResult)
                        }
                    }
                }
            }
    )
}

@Composable
fun SimpleSliceCanvas(image: ImageBitmap, rect: SegmentationRect, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val rW = rect.width.toInt()
        val rH = rect.height.toInt()
        val safeW = rW.coerceAtMost(image.width - rect.left)
        val safeH = rH.coerceAtMost(image.height - rect.top)

        if (safeW > 0 && safeH > 0) {
            drawImage(
                image = image,
                srcOffset = IntOffset(rect.left, rect.top),
                srcSize = IntSize(safeW, safeH),
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                filterQuality = FilterQuality.None
            )
        }
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