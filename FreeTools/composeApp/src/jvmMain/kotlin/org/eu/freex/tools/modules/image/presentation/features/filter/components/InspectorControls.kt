package org.eu.freex.tools.modules.image.presentation.features.filter.components


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.modules.image.domain.model.AutoCropFilter
import org.eu.freex.tools.modules.image.domain.model.BinarizationFilter
import org.eu.freex.tools.modules.image.domain.model.BlackWhiteInvertFilter
import org.eu.freex.tools.modules.image.domain.model.DenoiseFilter
import org.eu.freex.tools.modules.image.domain.model.DeskewFilter
import org.eu.freex.tools.modules.image.domain.model.ExtendCropFilter
import org.eu.freex.tools.modules.image.domain.model.ExtractBlobsFilter
import org.eu.freex.tools.modules.image.domain.model.ExtractContoursFilter
import org.eu.freex.tools.modules.image.domain.model.GrayscaleFilter
import org.eu.freex.tools.modules.image.domain.model.ImageFilter
import org.eu.freex.tools.modules.image.domain.model.MorphologyFilter
import org.eu.freex.tools.modules.image.domain.model.MultiColorFilter
import org.eu.freex.tools.modules.image.domain.model.PosterizationFilter
import org.eu.freex.tools.modules.image.domain.model.RemoveLinesFilter
import org.eu.freex.tools.modules.image.domain.model.RemoveNoiseFilter
import org.eu.freex.tools.modules.image.domain.model.ResizeScaleFilter
import org.eu.freex.tools.modules.image.domain.model.RotationFilter
import org.eu.freex.tools.modules.image.domain.model.SmartLayoutFilter


@Composable
fun FilterSelectionList(
    modifier: Modifier = Modifier,
    currentFilter: ImageFilter,
    onFilterChange: (ImageFilter) -> Unit
) {
    // 准备数据
    val colorFilters = remember {
        listOf(
            BinarizationFilter(),
            MultiColorFilter(),
            PosterizationFilter(),
            GrayscaleFilter(),
        )
    }
    val bwFilters = remember {
        listOf(
            RemoveNoiseFilter(),
            RemoveLinesFilter(),
            ExtractContoursFilter(),
            ExtractBlobsFilter(),
            DeskewFilter(),
            RotationFilter(),
            BlackWhiteInvertFilter(),
            DenoiseFilter(),
            MorphologyFilter(),
            SmartLayoutFilter(),
            AutoCropFilter(),
        )
    }
    val commonFilters = remember {
        listOf(
            ResizeScaleFilter(),
            ExtendCropFilter(),
        )
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        FilterUsageGuide()
        FilterGroup(
            title = "针对彩色进行处理:",
            filters = colorFilters,
            currentFilter = currentFilter,
            onSelect = onFilterChange
        )

        FilterGroup(
            title = "针对黑白进行处理:",
            filters = bwFilters,
            currentFilter = currentFilter,
            onSelect = onFilterChange
        )

        FilterGroup(
            title = "通用预处理:",
            filters = commonFilters,
            currentFilter = currentFilter,
            onSelect = onFilterChange
        )
    }
}

@Composable
private fun FilterGroup(
    title: String,
    filters: List<ImageFilter>,
    currentFilter: ImageFilter,
    onSelect: (ImageFilter) -> Unit
) {
    Column {
        androidx.compose.material3.Text(
            text = title,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        val rows = remember(filters) { filters.chunked(3) }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { rowFilters ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (i in 0 until 3) {
                        if (i < rowFilters.size) {
                            val filter = rowFilters[i]
                            val isSelected = currentFilter::class == filter::class
                            FilterChip(
                                text = filter.name,
                                isSelected = isSelected,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (!isSelected) {
                                        onSelect(filter)
                                    }
                                }
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterChip(
    modifier: Modifier = Modifier,
    text: String,
    isSelected: Boolean,
    tooltipText: String? = null,
    onClick: () -> Unit
) {
    // 【新增】判断是否有 Tooltip 文本
    if (!tooltipText.isNullOrBlank()) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = {
                PlainTooltip {
                    Text(tooltipText)
                }
            },
            state = rememberTooltipState()
        ) {
            ChipContent(modifier, isSelected, text, onClick)
        }
    } else {
        // 如果没有 Tooltip 文本，直接显示 Chip
        ChipContent(modifier, isSelected, text, onClick)
    }
}

@Composable
fun ChipContent(
    modifier: Modifier,
    isSelected: Boolean,
    text: String,
    onClick: () -> Unit
) {
    val backgroundColor =
        if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val contentColor =
        if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(4.dp),
        border = if (isSelected) BorderStroke(1.dp, borderColor) else null,
        modifier = modifier
            .height(32.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(
                text = text,
                color = contentColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


/**
 * 滤镜搭配指南 (全功能规划版)
 */
@Composable
private fun FilterUsageGuide() {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 标题行
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "滤镜搭配指南 (含高级功能预览)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 展开的内容区：涵盖了图中展示的所有核心功能
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {

                    // 1. 标准流程
                    GuideItem(
                        title = "1. 标准OCR流程 (白纸黑字)",
                        content = "灰度(Gray) → 二值化(OTSU/Auto) → [如变黑底则反色]"
                    )
                    GuideDescription("最通用的组合，适合文档、清晰截图。")
                    GuideDivider()

                    // 2. 笔画形态修复 (针对 膨胀/腐蚀)
                    GuideItem(
                        title = "2. 笔画修复 (断笔/粘连)",
                        content = "二值化 → 膨胀(Dilate)[加粗补断] / 腐蚀(Erode)[变细分粘]"
                    )
                    GuideDescription("字太细看不清用膨胀；字太粗粘在一起用腐蚀。")
                    GuideDivider()

                    // 3. 复杂背景处理
                    GuideItem(
                        title = "3. 复杂背景/干扰线去除",
                        content = "中值滤波(Median) → 灰度 → 二值化 → 去掉直线/清除杂点"
                    )
                    GuideDescription("中值滤波去椒盐噪点；二值化后用‘清除杂点’去孤立色块。")
                    GuideDivider()

                    // 4. 颜色与特效
                    GuideItem(
                        title = "4. 提取特定颜色 (血条/发光字)",
                        content = "颜色选取/二值化(RGB手动) → (色调分离) → 灰度"
                    )
                    GuideDescription("不先转灰度，直接提取红/黄等特定颜色通道。")
                    GuideDivider()

                    // 5. 几何矫正与结构
                    GuideItem(
                        title = "5. 几何矫正与结构分析",
                        content = "倾斜矫正(Deskew) → 细化(Thinning)[骨架提取]"
                    )
                    GuideDescription("修正歪斜的扫描件；细化用于分析汉字骨架结构。")
                }
            }
        }
    }
}

@Composable
private fun GuideItem(title: String, content: String) {
    Column(modifier = Modifier.padding(top = 6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = content,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary, // 流程公式用主色高亮
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun GuideDescription(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun GuideDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}