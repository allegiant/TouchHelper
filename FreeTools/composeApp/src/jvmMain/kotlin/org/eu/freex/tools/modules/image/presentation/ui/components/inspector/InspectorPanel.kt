package org.eu.freex.tools.modules.image.presentation.ui.components.inspector

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Divider
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.TabRowDefaults
import androidx.compose.material.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uniffi.touch_core.ImageFilter

/**
 * 右侧属性面板 (Inspector) 主入口
 * 包含：滤镜参数设置
 */
@Composable
fun InspectorPanel(
    modifier: Modifier = Modifier,
    selectedTab: Int,
    currentFilter: ImageFilter,
    thresholdRange: ClosedFloatingPointRange<Float>,
    isRgbAvgEnabled: Boolean,
    onTabChange: (Int) -> Unit,
    onFilterChange: (ImageFilter) -> Unit,
    onThresholdChange: (ClosedFloatingPointRange<Float>) -> Unit,
    onRgbAvgChange: (Boolean) -> Unit,
    onAddStep: () -> Unit,
    onModifyStep: () -> Unit
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
                1 -> {
                    // 占位：字符切割或其他功能
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("规则设置已移除", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}