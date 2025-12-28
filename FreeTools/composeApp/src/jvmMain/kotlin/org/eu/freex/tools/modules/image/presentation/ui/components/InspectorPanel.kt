package org.eu.freex.tools.modules.image.presentation.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.eu.freex.tools.model.*

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
    onRuleUpdate: (Long, String) -> Unit,
    onRuleToggle: (Long, Boolean) -> Unit,
    onRuleRemove: (Long) -> Unit
) {
    Column(modifier = modifier.background(Color(0xFFF3F3F3))) {
        Row(modifier = Modifier.height(32.dp).fillMaxWidth().background(Color(0xFFE0E0E0))) {
            TabButton("滤镜处理", selectedTab == 0, { onTabChange(0) }, Modifier.weight(1f))
            TabButton("切割识别", selectedTab == 1, { onTabChange(1) }, Modifier.weight(1f))
        }

        Divider(color = Color.LightGray)

        Box(modifier = Modifier.weight(1f).padding(8.dp)) {
            when (selectedTab) {
                0 -> FilterSettingsContent(
                    currentFilter, onFilterChange,
                    thresholdRange, onThresholdChange, isRgbAvgEnabled, onRgbAvgChange,
                    onAddStep, onModifyStep
                )
                1 -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("该功能暂时下线维护中", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun FilterSettingsContent(
    currentFilter: ImageFilter,
    onFilterChange: (ImageFilter) -> Unit,
    thresholdRange: ClosedFloatingPointRange<Float>,
    onThresholdChange: (ClosedFloatingPointRange<Float>) -> Unit,
    isRgbAvg: Boolean,
    onRgbAvgChange: (Boolean) -> Unit,
    onAdd: () -> Unit,
    onModify: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 8.dp)) {
            FilterGroupSection(ColorFilterType.TITLE, ColorFilterType.COLOR, ColorFilterType.entries, currentFilter, onFilterChange)
            Spacer(Modifier.height(12.dp))
            FilterGroupSection(BlackWhiteFilterType.TITLE, BlackWhiteFilterType.COLOR, BlackWhiteFilterType.entries, currentFilter, onFilterChange)
            Spacer(Modifier.height(12.dp))
            FilterGroupSection(CommonFilterType.TITLE, CommonFilterType.COLOR, CommonFilterType.entries, currentFilter, onFilterChange)

            if (currentFilter == ColorFilterType.BINARIZATION) {
                Spacer(Modifier.height(16.dp))
                Card(elevation = 2.dp, backgroundColor = Color(0xFFE3F2FD), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("二值化参数配置", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("阈值:", fontSize = 12.sp)
                            Spacer(Modifier.width(8.dp))
                            Text("${thresholdRange.start.toInt()} - ${thresholdRange.endInclusive.toInt()}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        RangeSlider(value = thresholdRange, onValueChange = onThresholdChange, valueRange = 0f..255f, colors = SliderDefaults.colors(thumbColor = Color(0xFF1565C0), activeTrackColor = Color(0xFF1565C0)))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = isRgbAvg, onCheckedChange = onRgbAvgChange, colors = CheckboxDefaults.colors(checkedColor = Color(0xFF1565C0)))
                            Text("使用 RGB 平均值", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Surface(elevation = 8.dp, modifier = Modifier.fillMaxWidth().background(Color.White)) {
            Column(Modifier.padding(8.dp)) {
                if (currentFilter != ViewFilter) {
                    Text("当前: ${currentFilter.label}", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
                }
                Row(Modifier.fillMaxWidth()) {
                    Button(onClick = onAdd, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(4.dp), colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF007ACC), contentColor = Color.White), enabled = currentFilter != ViewFilter) {
                        Text("添加步骤", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onModify, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(4.dp), colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFFFA000), contentColor = Color.White), enabled = currentFilter != ViewFilter) {
                        Text("修改步骤", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// 辅助组件 (简化版，确保存在)
@Composable fun FilterGroupSection(title: String, titleColor: Color, filters: List<ImageFilter>, currentFilter: ImageFilter, onSelect: (ImageFilter) -> Unit) {
    Column {
        SectionHeader(title, titleColor)
        Spacer(Modifier.height(6.dp))
        val columns = 2
        val chunkedFilters = filters.chunked(columns)
        chunkedFilters.forEach { rowFilters ->
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                rowFilters.forEachIndexed { index, filter ->
                    Box(Modifier.weight(1f)) { FilterGridButton(text = filter.label, isSelected = (currentFilter == filter), onClick = { onSelect(filter) }, modifier = Modifier.fillMaxWidth()) }
                    if (index < rowFilters.size - 1) Spacer(Modifier.width(4.dp))
                }
                if (rowFilters.size < columns) { repeat(columns - rowFilters.size) { Spacer(Modifier.width(4.dp)); Spacer(Modifier.weight(1f)) } }
            }
        }
    }
}
@Composable fun SectionHeader(title: String, color: Color) { Row(verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(width = 4.dp, height = 16.dp).background(color, RoundedCornerShape(2.dp))); Spacer(Modifier.width(6.dp)); Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black) } }
@Composable fun TabButton(text: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) { Box(modifier = modifier.fillMaxHeight().background(if (isSelected) Color(0xFFF3F3F3) else Color(0xFFE0E0E0)).clickable(onClick = onClick), contentAlignment = Alignment.Center) { Column(Modifier.fillMaxSize()) { Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text(text, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp, color = if(isSelected) Color(0xFF007ACC) else Color.Black) }; if (isSelected) Box(Modifier.height(2.dp).fillMaxWidth().background(Color(0xFF007ACC))) } } }
@Composable fun FilterGridButton(text: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) { Button(onClick = onClick, modifier = modifier.height(32.dp), contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(4.dp), colors = ButtonDefaults.buttonColors(backgroundColor = if (isSelected) Color(0xFFE3F2FD) else Color.White, contentColor = if (isSelected) Color(0xFF1565C0) else Color.DarkGray), border = if (isSelected) BorderStroke(1.dp, Color(0xFF1565C0)) else BorderStroke(1.dp, Color(0xFFE0E0E0)), elevation = ButtonDefaults.elevation(0.dp, 0.dp)) { Text(text, fontSize = 12.sp) } }