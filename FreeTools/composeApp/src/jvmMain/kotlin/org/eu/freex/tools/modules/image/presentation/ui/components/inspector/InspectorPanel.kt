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
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.TabRowDefaults
import androidx.compose.material.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.eu.freex.tools.modules.image.presentation.contract.ImageUiEvent
import org.eu.freex.tools.modules.image.presentation.ui.components.inspector.core.LocalImageViewModel

/**
 * 右侧属性面板 (Inspector) - [修正版]
 * 去除了底部的重复函数定义，直接引用 SharedComponents.kt 中的组件
 */
@Composable
fun InspectorPanel(
    modifier: Modifier = Modifier,
    selectedTab: Int,
    onTabChange: (Int) -> Unit
) {
    val viewModel = LocalImageViewModel.current
    val state by viewModel.uiState.collectAsState()

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
                // Case 0: 滤镜处理 Tab
                0 -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // A. 滤镜选择列表
                        FilterSelectionList(
                            modifier = Modifier.weight(1f),
                            currentFilter = state.currentFilter,
                            onFilterChange = { newFilter ->
                                viewModel.handleEvent(ImageUiEvent.SelectFilter(newFilter))
                            }
                        )

                        Divider(color = Color(0xFF3E3E42))

                        // B. 底部参数控制区 (固定高度，不滚动)
                        Column(
                            modifier = Modifier
                                .background(Color(0xFF1E1E1E))
                                .padding(12.dp)
                        ) {
                            // 这里直接使用 SharedComponents.kt 里的 SectionHeader
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
                                // 按钮 1: 更新当前步骤
                                Button(
                                    onClick = { viewModel.handleEvent(ImageUiEvent.ModifyCurrentStep) },
                                    modifier = Modifier.weight(1f).height(32.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = Color(0xFF3E3E42),
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("更新当前步骤", fontSize = 12.sp)
                                }

                                // 按钮 2: 添加新步骤
                                Button(
                                    onClick = { viewModel.handleEvent(ImageUiEvent.ApplyCurrentFilter) },
                                    modifier = Modifier.weight(1f).height(32.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = Color(0xFF007ACC),
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("添加新步骤", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // Case 1: 字符切割 Tab
                1 -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("规则设置功能开发中...", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}