package org.eu.freex.tools.modules.image.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.eu.freex.tools.model.*
import uniffi.touch_core.BlackWhiteFilterType
import uniffi.touch_core.ColorFilterType
import uniffi.touch_core.ColorRule
import uniffi.touch_core.CommonFilterType
import uniffi.touch_core.ImageFilter

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

    // 规则相关
    onRuleUpdate: (Long, ColorRule) -> Unit,
    onRuleToggle: (Long, Boolean) -> Unit,
    onRuleRemove: (Long) -> Unit
) {
    Column(modifier = modifier.background(Color(0xFF252526)).padding(8.dp)) {

        // 1. 顶部 Tab
        TabRow(
            selectedTabIndex = selectedTab,
            backgroundColor = Color.Transparent,
            contentColor = Color.White,
            modifier = Modifier.height(40.dp)
        ) {
            listOf("彩色", "黑白", "通用", "规则").forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { onTabChange(index) },
                    text = { Text(title, fontSize = 12.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 2. 内容区域
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> ColorFilterList(currentFilter, onFilterChange)
                1 -> BlackWhiteFilterList(currentFilter, onFilterChange)
                2 -> CommonFilterList(currentFilter, onFilterChange)
                3 -> RuleList(colorRules, onRuleUpdate, onRuleToggle, onRuleRemove)
            }
        }

        Divider(color = Color.Gray, thickness = 0.5.dp)

        // 3. 底部参数控制区 (仅当不是规则 Tab 时显示)
        if (selectedTab != 3) {
            FilterSettingsPanel(
                currentFilter = currentFilter,
                thresholdRange = thresholdRange,
                isRgbAvgEnabled = isRgbAvgEnabled,
                onThresholdChange = onThresholdChange,
                onRgbAvgChange = onRgbAvgChange,
                onAddStep = onAddStep,
                onModifyStep = onModifyStep
            )
        }
    }
}

// --- 子组件：滤镜列表 ---

@Composable
fun ColorFilterList(
    currentFilter: ImageFilter,
    onSelect: (ImageFilter) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item { SectionTitle("针对彩色进行处理") }
        items(ColorFilterType.entries) { item ->
            // 【关键修改】判断选中状态需要解包 v1
            val isSelected = (currentFilter is ImageFilter.Color && currentFilter.v1 == item)
            FilterItemCard(
                label = item.label,
                desc = item.description,
                isSelected = isSelected,
                // 【关键修改】点击时包装为 ImageFilter.Color
                onClick = { onSelect(ImageFilter.Color(item)) }
            )
        }
    }
}

@Composable
fun BlackWhiteFilterList(
    currentFilter: ImageFilter,
    onSelect: (ImageFilter) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item { SectionTitle("针对黑白进行处理") }
        items(BlackWhiteFilterType.entries) { item ->
            val isSelected = (currentFilter is ImageFilter.BlackWhite && currentFilter.v1 == item)
            FilterItemCard(
                label = item.label,
                desc = item.description,
                isSelected = isSelected,
                // 【关键修改】点击时包装为 ImageFilter.BlackWhite
                onClick = { onSelect(ImageFilter.BlackWhite(item)) }
            )
        }
    }
}

@Composable
fun CommonFilterList(
    currentFilter: ImageFilter,
    onSelect: (ImageFilter) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item { SectionTitle("通用预处理") }
        items(CommonFilterType.entries) { item ->
            val isSelected = (currentFilter is ImageFilter.Common && currentFilter.v1 == item)
            FilterItemCard(
                label = item.label,
                desc = item.description,
                isSelected = isSelected,
                // 【关键修改】点击时包装为 ImageFilter.Common
                onClick = { onSelect(ImageFilter.Common(item)) }
            )
        }
    }
}

@Composable
fun RuleList(
    rules: List<ColorRule>,
    onUpdate: (Long, ColorRule) -> Unit,
    onToggle: (Long, Boolean) -> Unit,
    onRemove: (Long) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("颜色规则列表")
                Spacer(Modifier.weight(1f))
                // 添加规则的按钮逻辑需要 viewModel 支持，暂时留空或通过上层传递
            }
        }
        items(rules) { rule ->
            Card(
                backgroundColor = Color(0xFF333333),
                elevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(8.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = rule.isEnabled,
                        onCheckedChange = { onToggle(rule.id, it) },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFFFF8A80))
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("目标: ${rule.targetHex}", color = Color.White, fontSize = 12.sp)
                        Text("偏离: ${rule.biasHex}", color = Color.Gray, fontSize = 12.sp)
                    }
                    IconButton(onClick = { onRemove(rule.id) }) {
                        Icon(Icons.Default.Delete, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun FilterSettingsPanel(
    currentFilter: ImageFilter,
    thresholdRange: ClosedFloatingPointRange<Float>,
    isRgbAvgEnabled: Boolean,
    onThresholdChange: (ClosedFloatingPointRange<Float>) -> Unit,
    onRgbAvgChange: (Boolean) -> Unit,
    onAddStep: () -> Unit,
    onModifyStep: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text("参数设置", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))

        // 二值化参数
        if (currentFilter is ImageFilter.Color && currentFilter.v1 == ColorFilterType.BINARIZATION) {
            Text("阈值范围: ${thresholdRange.start.toInt()} - ${thresholdRange.endInclusive.toInt()}", color = Color.Gray, fontSize = 12.sp)
            RangeSlider(
                value = thresholdRange,
                onValueChange = onThresholdChange,
                valueRange = 0f..255f,
                colors = SliderDefaults.colors(thumbColor = Color(0xFFFF8A80), activeTrackColor = Color(0xFFFF8A80))
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = isRgbAvgEnabled,
                    onCheckedChange = onRgbAvgChange,
                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFFFF8A80))
                )
                Text("使用 RGB 平均值", color = Color.White, fontSize = 12.sp)
            }
        } else {
            Text("无可用参数", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(vertical = 16.dp))
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onModifyStep,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF424242))
            ) {
                Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp), tint = Color.White)
                Spacer(Modifier.width(4.dp))
                Text("修改当前", color = Color.White, fontSize = 12.sp)
            }
            Button(
                onClick = onAddStep,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFFF8A80))
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp), tint = Color.White)
                Spacer(Modifier.width(4.dp))
                Text("添加步骤", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

// --- 通用组件 ---

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        color = Color(0xFFFF8A80),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
fun FilterItemCard(
    label: String,
    desc: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        backgroundColor = if (isSelected) Color(0xFF3E3E42) else Color(0xFF2D2D30),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF8A80)) else null,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(label, color = if (isSelected) Color(0xFFFF8A80) else Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(desc, color = Color.Gray, fontSize = 11.sp, maxLines = 2)
        }
    }
}