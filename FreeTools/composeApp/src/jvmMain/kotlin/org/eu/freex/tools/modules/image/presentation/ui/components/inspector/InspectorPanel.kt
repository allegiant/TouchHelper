package org.eu.freex.tools.modules.image.presentation.ui.components.inspector

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.* // 【关键】引入 Material3
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.modules.image.presentation.contract.ImageUiEvent
import org.eu.freex.tools.modules.image.presentation.ui.components.inspector.core.LocalImageViewModel

/**
 * 右侧属性面板 (Inspector) - [Material 3 适配版]
 */
@Composable
fun InspectorPanel(
    modifier: Modifier = Modifier,
    selectedTab: Int,
    onTabChange: (Int) -> Unit
) {
    val viewModel = LocalImageViewModel.current
    val state by viewModel.uiState.collectAsState()

    // 定义区域背景色
    val panelBackground = MaterialTheme.colorScheme.surface
    val tabContainerColor = MaterialTheme.colorScheme.surfaceContainer
    val controlAreaColor = MaterialTheme.colorScheme.surfaceContainer

    Column(
        modifier = modifier
            .background(panelBackground)
            .fillMaxHeight()
    ) {
        // --- 1. 顶部 Tab 栏 ---
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = tabContainerColor,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) },
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { onTabChange(0) },
                text = {
                    Text(
                        "滤镜处理",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { onTabChange(1) },
                text = {
                    Text(
                        "字符切割",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // --- 2. 内容区域 ---
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                // Case 0: 滤镜处理 Tab
                0 -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // A. 滤镜选择列表
                        // 注意：确保 FilterSelectionList 内部也使用了 M3 颜色或接受 modifier 背景
                        FilterSelectionList(
                            modifier = Modifier.weight(1f),
                            currentFilter = state.currentFilter,
                            onFilterChange = { newFilter ->
                                viewModel.handleEvent(ImageUiEvent.SelectFilter(newFilter))
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // B. 底部参数控制区 (固定高度，不滚动)
                        Column(
                            modifier = Modifier
                                .background(controlAreaColor)
                                .padding(12.dp)
                        ) {
                            // SectionHeader 需确保在 SharedComponents.kt 中已适配 M3
                            SectionHeader("参数调节")

                            Spacer(Modifier.height(12.dp))

                            // 动态加载具体的滤镜设置 UI
                            val renderer = remember(state.currentFilter) {
                                FilterUIRegistry.getRenderer(state.currentFilter)
                            }
                            renderer.Content()

                            Spacer(Modifier.height(16.dp))

                            // --- 通用操作按钮 ---
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // 按钮 1: 更新当前步骤 (使用 Tonal 按钮，层级稍低)
                                Button(
                                    onClick = { viewModel.handleEvent(ImageUiEvent.ModifyCurrentStep) },
                                    modifier = Modifier.weight(1f).height(36.dp), // M3 标准高度通常为 40dp，这里保持紧凑 36dp
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    contentPadding = PaddingValues(0.dp),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text("更新当前步骤", style = MaterialTheme.typography.labelMedium)
                                }

                                // 按钮 2: 添加新步骤 (使用 Filled 按钮，主操作)
                                Button(
                                    onClick = { viewModel.handleEvent(ImageUiEvent.ApplyCurrentFilter) },
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    contentPadding = PaddingValues(0.dp),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text("添加新步骤", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }

                // Case 1: 字符切割 Tab
                1 -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "规则设置功能开发中...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}