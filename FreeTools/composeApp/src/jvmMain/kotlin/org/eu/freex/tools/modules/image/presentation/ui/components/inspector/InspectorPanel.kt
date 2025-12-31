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
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.modules.image.presentation.contract.ProjectState
import org.eu.freex.tools.modules.image.presentation.contract.UiInteractionState
import org.eu.freex.tools.modules.image.presentation.contract.events.ApplyCurrentFilter
import org.eu.freex.tools.modules.image.presentation.contract.events.ChangePanelTab
import org.eu.freex.tools.modules.image.presentation.contract.events.ModifyCurrentStep
import org.eu.freex.tools.modules.image.presentation.contract.events.SelectFilter
import org.eu.freex.tools.modules.image.presentation.ui.components.inspector.core.LocalImageViewModel

/**
 * 右侧属性面板 (Inspector) - [Material 3 适配版]
 * * 重构说明：
 * 1. 移除了内部的 collectAsState，改为由父组件传入切分后的状态 (ProjectState, UiInteractionState)。
 * 2. 这样当 CanvasState (画布缩放) 变化时，InspectorPanel 不会发生重组。
 */
@Composable
fun InspectorPanel(
    modifier: Modifier = Modifier,
    // 【关键修改】只接收需要的状态切片
    projectState: ProjectState,
    uiState: UiInteractionState
) {
    // 依然需要 ViewModel 来分发事件，但不监听它的流
    val viewModel = LocalImageViewModel.current

    // 从 uiState 中获取当前 Tab 索引
    val selectedTab = uiState.rightPanelTabIndex

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
                if (selectedTab < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                // 【关键修改】使用事件来切换 Tab
                onClick = { viewModel.handleEvent(ChangePanelTab(0)) },
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
                onClick = { viewModel.handleEvent(ChangePanelTab(1)) },
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
                        FilterSelectionList(
                            modifier = Modifier.weight(1f),
                            // 【关键修改】使用 projectState 中的数据
                            currentFilter = projectState.currentFilter,
                            onFilterChange = { newFilter ->
                                viewModel.handleEvent(SelectFilter(newFilter))
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // B. 底部参数控制区
                        Column(
                            modifier = Modifier
                                .background(controlAreaColor)
                                .padding(12.dp)
                        ) {
                            SectionHeader("参数调节")

                            Spacer(Modifier.height(12.dp))

                            // 动态加载具体的滤镜设置 UI
                            // 使用 remember 缓存 Renderer，只有当 Filter 类型或引用变化时才重新获取
                            val renderer = remember(projectState.currentFilter) {
                                FilterUIRegistry.getRenderer(projectState.currentFilter)
                            }
                            renderer.Content()

                            Spacer(Modifier.height(16.dp))

                            // --- 通用操作按钮 ---
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // 按钮 1: 更新当前步骤
                                Button(
                                    onClick = { viewModel.handleEvent(ModifyCurrentStep) },
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    contentPadding = PaddingValues(0.dp),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text("更新当前步骤", style = MaterialTheme.typography.labelMedium)
                                }

                                // 按钮 2: 添加新步骤
                                Button(
                                    onClick = { viewModel.handleEvent(ApplyCurrentFilter) },
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