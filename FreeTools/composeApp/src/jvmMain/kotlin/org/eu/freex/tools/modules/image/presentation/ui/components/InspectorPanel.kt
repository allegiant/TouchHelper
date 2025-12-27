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

/**
 * 4. 右侧属性面板 (InspectorPanel)
 * 包含 Tab 切换，负责展示滤镜参数、切割网格设置和颜色规则。
 */

@Composable
fun InspectorPanel(
    modifier: Modifier = Modifier,
    selectedTab: Int, // 0: 滤镜, 1: 切割

    // 滤镜数据
    currentFilter: ImageFilter,
    thresholdRange: ClosedFloatingPointRange<Float>,
    isRgbAvgEnabled: Boolean,
    colorRules: List<ColorRule>, // 规则列表

    // 切割数据
    isGridMode: Boolean,
    gridParams: GridParams,

    // 事件回调
    onTabChange: (Int) -> Unit,
    onFilterChange: (ImageFilter) -> Unit,
    onThresholdChange: (ClosedFloatingPointRange<Float>) -> Unit,
    onRgbAvgChange: (Boolean) -> Unit,
    onApplyFilter: () -> Unit,

    onGridModeToggle: (Boolean) -> Unit,
    onGridParamsChange: (GridParams) -> Unit,
    onPerformSegmentation: () -> Unit,

    // 规则回调 (简化演示)
    onRuleUpdate: (Long, String) -> Unit,
    onRuleToggle: (Long, Boolean) -> Unit,
    onRuleRemove: (Long) -> Unit
) {
    Column(modifier = modifier.background(Color(0xFFF3F3F3))) {
        // --- 顶部 Tab ---
        Row(modifier = Modifier.height(32.dp).fillMaxWidth().background(Color(0xFFE0E0E0))) {
            TabButton("滤镜处理", selectedTab == 0, { onTabChange(0) }, Modifier.weight(1f))
            TabButton("切割识别", selectedTab == 1, { onTabChange(1) }, Modifier.weight(1f))
        }

        Divider(color = Color.LightGray)

        // --- 内容区 ---
        Box(modifier = Modifier.weight(1f).padding(8.dp)) {
            when (selectedTab) {
                0 -> FilterSettingsContent(
                    currentFilter, onFilterChange,
                    thresholdRange, onThresholdChange, isRgbAvgEnabled, onRgbAvgChange,
                    onApplyFilter
                )
                1 -> SegmentationSettingsContent(
                    isGridMode, onGridModeToggle,
                    gridParams, onGridParamsChange,
                    onPerformSegmentation
                )
            }
        }
    }
}

// --- 滤镜设置内容 ---
@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun FilterSettingsContent(
    currentFilter: ImageFilter,
    onFilterChange: (ImageFilter) -> Unit,
    thresholdRange: ClosedFloatingPointRange<Float>,
    onThresholdChange: (ClosedFloatingPointRange<Float>) -> Unit,
    isRgbAvg: Boolean,
    onRgbAvgChange: (Boolean) -> Unit,
    onApply: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("选择滤镜:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        Spacer(Modifier.height(4.dp))

        // 滤镜选择网格
        val filters = ColorFilterType.entries + BlackWhiteFilterType.entries + CommonFilterType.entries
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.height(300.dp), // 固定高度，内部滚动
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(filters) { filter ->
                FilterButton(filter.label, currentFilter == filter) { onFilterChange(filter) }
            }
        }

        Spacer(Modifier.height(16.dp))
        Divider()
        Spacer(Modifier.height(8.dp))

        // 二值化参数面板
        if (currentFilter == ColorFilterType.BINARIZATION) {
            Text("二值化参数:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))

            Text("阈值范围: ${thresholdRange.start.toInt()} - ${thresholdRange.endInclusive.toInt()}", fontSize = 11.sp)
            RangeSlider(
                value = thresholdRange,
                onValueChange = onThresholdChange,
                valueRange = 0f..255f, // 假设0-255，根据业务调整
                steps = 255
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isRgbAvg, onCheckedChange = onRgbAvgChange)
                Text("使用 RGB 平均值", fontSize = 12.sp)
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onApply,
            modifier = Modifier.fillMaxWidth().height(40.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF007ACC), contentColor = Color.White)
        ) {
            Text("应用滤镜")
        }
    }
}

// --- 切割设置内容 ---
@Composable
private fun SegmentationSettingsContent(
    isGridMode: Boolean,
    onGridModeToggle: (Boolean) -> Unit,
    gridParams: GridParams,
    onParamsChange: (GridParams) -> Unit,
    onExecute: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("切割模式:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row {
            FilterButton("固定网格", isGridMode) { onGridModeToggle(true) }
            Spacer(Modifier.width(4.dp))
            FilterButton("连通区域", !isGridMode) { onGridModeToggle(false) }
        }

        Spacer(Modifier.height(16.dp))

        if (isGridMode) {
            Text("网格参数 (XY / WH / Gaps / Counts):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            // 简单的参数输入框列表
            NumberInput("X (起始)", gridParams.x) { onParamsChange(gridParams.copy(x = it)) }
            NumberInput("Y (起始)", gridParams.y) { onParamsChange(gridParams.copy(y = it)) }
            NumberInput("Width", gridParams.w) { onParamsChange(gridParams.copy(w = it)) }
            NumberInput("Height", gridParams.h) { onParamsChange(gridParams.copy(h = it)) }
            NumberInput("Col Count", gridParams.colCount) { onParamsChange(gridParams.copy(colCount = it)) }
            NumberInput("Row Count", gridParams.rowCount) { onParamsChange(gridParams.copy(rowCount = it)) }
        } else {
            Text("智能识别连通区域 (基于颜色规则)", fontSize = 12.sp, color = Color.Gray)
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onExecute,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4CAF50), contentColor = Color.White)
        ) {
            Text("开始切割")
        }
    }
}

// --- 通用小组件 ---

@Composable
fun TabButton(text: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(if (isSelected) Color(0xFFF3F3F3) else Color(0xFFE0E0E0))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp)
    }
}

@Composable
fun FilterButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(28.dp)
            .background(if (isSelected) Color(0xFF90CAF9) else Color.White, RoundedCornerShape(4.dp))
            .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 11.sp, color = if (isSelected) Color.Black else Color.DarkGray)
    }
}

@Composable
fun NumberInput(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.width(80.dp), fontSize = 11.sp)
        BasicTextField(
            value = value.toString(),
            onValueChange = { str ->
                if (str.isEmpty()) onChange(0)
                else str.toIntOrNull()?.let { onChange(it) }
            },
            textStyle = TextStyle(fontSize = 12.sp),
            modifier = Modifier
                .weight(1f)
                .background(Color.White)
                .border(1.dp, Color.Gray)
                .padding(4.dp)
        )
    }
}