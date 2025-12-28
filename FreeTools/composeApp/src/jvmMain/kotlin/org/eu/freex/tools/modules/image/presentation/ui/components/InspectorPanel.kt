package org.eu.freex.tools.modules.image.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.eu.freex.tools.model.FilterConstantsUI
import org.eu.freex.tools.model.label
import uniffi.touch_core.BlackWhiteFilterType
import uniffi.touch_core.ColorFilterType
import uniffi.touch_core.ColorRule
import uniffi.touch_core.CommonFilterType
import uniffi.touch_core.ImageFilter

/**
 * 右侧属性面板 (Inspector)
 * 包含：滤镜参数设置、二值化阈值、字符切割规则管理
 */
@Composable
fun InspectorPanel(
    modifier: Modifier = Modifier,
    selectedTab: Int,
    currentFilter: ImageFilter,
    thresholdRange: ClosedFloatingPointRange<Float>,
    isRgbAvgEnabled: Boolean,
    colorRules: List<ColorRule>,
    onTabChange: (Int) -> Unit,
    onFilterChange: (ImageFilter) -> Unit,
    onThresholdChange: (ClosedFloatingPointRange<Float>) -> Unit,
    onRgbAvgChange: (Boolean) -> Unit,
    onAddStep: () -> Unit,
    onModifyStep: () -> Unit,
    onRuleUpdate: (String, ColorRule) -> Unit,
    onRuleToggle: (String, Boolean) -> Unit,
    onRuleRemove: (String) -> Unit
) {
    Column(
        modifier = modifier
            .background(Color(0xFF252526))
            .fillMaxHeight()
    ) {
        // --- 1. 顶部 Tab 栏 ---
        TabRow(
            selectedTabIndex = selectedTab,
            backgroundColor = Color(0xFF1E1E1E),
            contentColor = Color(0xFFCCCCCC),
            divider = { Divider(color = Color(0xFF3E3E42), thickness = 1.dp) },
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = Color(0xFF007ACC)
                )
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { onTabChange(0) },
                text = { Text("滤镜处理", fontSize = 13.sp, fontWeight = FontWeight.Medium) },
                selectedContentColor = Color.White,
                unselectedContentColor = Color.Gray
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { onTabChange(1) },
                text = { Text("字符切割", fontSize = 13.sp, fontWeight = FontWeight.Medium) },
                selectedContentColor = Color.White,
                unselectedContentColor = Color.Gray
            )
        }

        // --- 2. 内容区域 ---
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> FilterSettings(
                    currentFilter = currentFilter,
                    thresholdRange = thresholdRange,
                    isRgbAvgEnabled = isRgbAvgEnabled,
                    onFilterChange = onFilterChange,
                    onThresholdChange = onThresholdChange,
                    onRgbAvgChange = onRgbAvgChange,
                    onAddStep = onAddStep,
                    onModifyStep = onModifyStep
                )
                1 -> SegmentationSettings(
                    rules = colorRules,
                    onUpdate = onRuleUpdate,
                    onToggle = onRuleToggle
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun FilterSettings(
    currentFilter: ImageFilter,
    thresholdRange: ClosedFloatingPointRange<Float>,
    isRgbAvgEnabled: Boolean,
    onFilterChange: (ImageFilter) -> Unit,
    onThresholdChange: (ClosedFloatingPointRange<Float>) -> Unit,
    onRgbAvgChange: (Boolean) -> Unit,
    onAddStep: () -> Unit,
    onModifyStep: () -> Unit
) {
    // 准备数据
    val colorFilters = remember { ColorFilterType.values().map { ImageFilter.Color(it) } }
    val bwFilters = remember { BlackWhiteFilterType.values().map { ImageFilter.BlackWhite(it) } }
    val commonFilters = remember { CommonFilterType.values().map { ImageFilter.Common(it) } }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // --- 上半部分：滤镜列表 (可滚动) ---
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp) // 组间距
        ) {
            // 1. 彩色处理组
            FilterGroup(
                title = FilterConstantsUI.TITLE_COLOR,
                filters = colorFilters,
                currentFilter = currentFilter,
                onSelect = onFilterChange
            )

            // 2. 黑白处理组
            FilterGroup(
                title = FilterConstantsUI.TITLE_BW,
                filters = bwFilters,
                currentFilter = currentFilter,
                onSelect = onFilterChange
            )

            // 3. 通用处理组
            FilterGroup(
                title = FilterConstantsUI.TITLE_COMMON,
                filters = commonFilters,
                currentFilter = currentFilter,
                onSelect = onFilterChange
            )
        }

        Divider(color = Color(0xFF3E3E42))

        // --- 下半部分：参数调节 (固定在底部) ---
        Column(
            modifier = Modifier
                .background(Color(0xFF1E1E1E))
                .padding(12.dp)
        ) {
            SectionHeader(title = "参数调节")

            Spacer(Modifier.height(8.dp))

            var hasControls = false

            // 阈值滑块
            val showThreshold = when (currentFilter) {
                is ImageFilter.Color -> currentFilter.v1 == ColorFilterType.BINARIZATION
                else -> false
            }

            if (showThreshold) {
                hasControls = true
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("阈值", color = Color.Gray, fontSize = 12.sp)
                    Text("${thresholdRange.start.toInt()} - ${thresholdRange.endInclusive.toInt()}", color = Color.Gray, fontSize = 12.sp)
                }
                RangeSlider(
                    value = thresholdRange,
                    onValueChange = onThresholdChange,
                    valueRange = 0f..255f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF007ACC),
                        activeTrackColor = Color(0xFF007ACC),
                        inactiveTrackColor = Color(0xFF3E3E42)
                    )
                )
            }

            // RGB 平均值开关
            val showRgbAvg = when (currentFilter) {
                is ImageFilter.Color -> {
                    currentFilter.v1 == ColorFilterType.GRAYSCALE ||
                            currentFilter.v1 == ColorFilterType.BINARIZATION
                }
                else -> false
            }

            if (showRgbAvg) {
                hasControls = true
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onRgbAvgChange(!isRgbAvgEnabled) }
                ) {
                    Checkbox(
                        checked = isRgbAvgEnabled,
                        onCheckedChange = onRgbAvgChange,
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF007ACC))
                    )
                    Text("使用 RGB 平均值", color = Color.LightGray, fontSize = 12.sp)
                }
            }

            if (!hasControls) {
                Text("当前滤镜无可调参数", color = Color.DarkGray, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
            }

            Spacer(Modifier.height(16.dp))

            // 操作按钮
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onModifyStep,
                    modifier = Modifier.weight(1f).height(32.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF3E3E42), contentColor = Color.White),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("更新当前步骤", fontSize = 12.sp)
                }
                Button(
                    onClick = onAddStep,
                    modifier = Modifier.weight(1f).height(32.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF007ACC), contentColor = Color.White),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("添加新步骤", fontSize = 12.sp)
                }
            }
        }
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
        Text(
            text = title,
            color = Color(0xFF569CD6),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // 关键修改：使用 chunked(3) 将列表按 3 个一组分割
        // 然后使用 Row + weight 布局，实现严格的网格对齐
        val rows = remember(filters) { filters.chunked(3) }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { rowFilters ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 遍历 3 个位置，保证即便最后一行不满 3 个也能对齐
                    for (i in 0 until 3) {
                        if (i < rowFilters.size) {
                            val filter = rowFilters[i]
                            FilterChip(
                                text = filter.label,
                                isSelected = isSameFilter(currentFilter, filter),
                                modifier = Modifier.weight(1f), // 核心：均分宽度
                                onClick = { onSelect(filter) }
                            )
                        } else {
                            // 占位符，保持对齐
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(
    text: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) Color(0xFF094771) else Color(0xFF333333),
        shape = RoundedCornerShape(3.dp),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF007ACC)) else null,
        modifier = modifier
            .height(28.dp) // 稍微增加高度以容纳文字
            .clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Text(
                text = text,
                color = if (isSelected) Color.White else Color(0xFFCCCCCC),
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis // 防止文字过长撑坏布局
            )
        }
    }
}

private fun isSameFilter(a: ImageFilter, b: ImageFilter): Boolean {
    return a == b
}

@Composable
private fun SegmentationSettings(
    rules: List<ColorRule>,
    onUpdate: (String, ColorRule) -> Unit,
    onToggle: (String, Boolean) -> Unit
) {
    Column(modifier = Modifier.padding(12.dp)) {
        SectionHeader(title = "颜色提取规则")
        if (rules.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "暂无规则，请在左侧画布上取色",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 8.dp)
            ) {
                items(rules) { rule ->
                    ColorRuleItem(rule, onToggle)
                }
            }
        }
    }
}

@Composable
private fun ColorRuleItem(
    rule: ColorRule,
    onToggle: (String, Boolean) -> Unit
) {
    val color = try {
        val c = java.awt.Color.decode(rule.targetHex)
        Color(c.red, c.green, c.blue)
    } catch (e: Exception) {
        Color.Gray
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF333333), RoundedCornerShape(4.dp))
            .border(1.dp, Color(0xFF444444), RoundedCornerShape(4.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 颜色预览
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(color, RoundedCornerShape(4.dp))
                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(rule.targetHex, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("偏色: ${rule.biasHex}", color = Color.Gray, fontSize = 11.sp)
        }

        Switch(
            checked = rule.isEnabled,
            onCheckedChange = { onToggle(rule.id.toString(), it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF007ACC),
                checkedTrackColor = Color(0xFF007ACC).copy(alpha = 0.5f)
            )
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Tune, contentDescription = null, tint = Color(0xFF007ACC), modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(title, color = Color(0xFFCCCCCC), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}