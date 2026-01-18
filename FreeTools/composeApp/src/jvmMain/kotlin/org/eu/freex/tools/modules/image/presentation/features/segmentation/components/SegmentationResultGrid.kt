package org.eu.freex.tools.modules.image.presentation.features.segmentation.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.eu.freex.tools.modules.image.domain.model.SegmentationRect

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
fun SegmentationResultGrid(
    results: List<SegmentationRect>,
    labels: Map<Int, String>,
    selectedIndex: Int,
    sourceImage: ImageBitmap?,
    slicedImages: List<ImageBitmap?>,
    onSelectChar: (Int) -> Unit,
    // [新增] 双击回调
    onDoubleTap: (Int) -> Unit
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
                onItemClick = onSelectChar,
                // [新增] 传递双击事件
                onItemDoubleTap = onDoubleTap
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
    onItemClick: (Int) -> Unit,
    // [新增] 双击参数
    onItemDoubleTap: (Int) -> Unit
) {
    Spacer(
        modifier = Modifier
            .aspectRatio(1f)
            // [修改] 替换 clickable 为 pointerInput 以支持双击
            .combinedClickable(
                onClick = { onItemClick(index) },
                onDoubleClick = { onItemDoubleTap(index) }
            )
            .drawWithCache {
                // ... (原有的绘图逻辑保持完全不变) ...
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