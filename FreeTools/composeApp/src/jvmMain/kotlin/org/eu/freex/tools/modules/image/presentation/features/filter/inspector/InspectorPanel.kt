package org.eu.freex.tools.modules.image.presentation.features.filter.inspector

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
import org.eu.freex.tools.modules.image.domain.model.ViewFilter
import org.eu.freex.tools.modules.image.domain.model.type
import org.eu.freex.tools.modules.image.presentation.features.tools.ChangePanelTab
import org.eu.freex.tools.modules.image.presentation.features.pipeline.PipelineState
import org.eu.freex.tools.modules.image.presentation.features.project.ProjectState
import org.eu.freex.tools.modules.image.presentation.features.editor.UiInteractionState
import org.eu.freex.tools.modules.image.presentation.features.filter.ApplyNewStep
import org.eu.freex.tools.modules.image.presentation.features.filter.PreviewFilter
import org.eu.freex.tools.modules.image.presentation.features.filter.UpdateCurrentStep
import org.eu.freex.tools.modules.image.presentation.features.filter.inspector.core.LocalImageViewModel

/**
 * 右侧属性面板 (Inspector)
 * 状态解耦：完全依赖 PipelineState 中的 DraftState
 */
@Composable
fun InspectorPanel(
    modifier: Modifier = Modifier,
    projectState: ProjectState,   // 仅用于可能的信息展示，核心逻辑不依赖它
    pipelineState: PipelineState, // 【核心】提供 DraftState
    uiState: UiInteractionState
) {
    val viewModel = LocalImageViewModel.current
    val selectedTab = uiState.rightPanelTabIndex

    // 从 Pipeline 的草稿状态中获取当前应该显示的滤镜
    // 无论是“回显旧步骤”还是“点击新滤镜”，数据源都是 draft.activeFilter
    val currentActiveFilter = pipelineState.draft.activeFilter

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .fillMaxHeight()
    ) {
        // --- 1. Tab 栏 ---
        InspectorTabs(selectedTab) { viewModel.handleEvent(ChangePanelTab(it)) }

        // --- 2. 内容区域 ---
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> { // 滤镜处理 Tab
                    Column(modifier = Modifier.fillMaxSize()) {
                        // A. 滤镜选择列表
                        // 选择滤镜时，触发 PreviewFilter，forceReloadBaseImage = false (基于当前 Draft 输入)
                        FilterSelectionList(
                            modifier = Modifier.weight(1f),
                            currentFilter = currentActiveFilter,
                            onFilterChange = { newFilter ->
                                viewModel.handleEvent(PreviewFilter(newFilter, forceReloadBaseImage = false))
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // B. 参数控制区
                        Column(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .padding(12.dp)
                        ) {
                            SectionHeader("参数调节")
                            Spacer(Modifier.height(12.dp))

                            // 动态渲染具体滤镜的 UI
                            // 所有的滑块变动，都应该触发 PreviewFilter
                            val renderer = remember(currentActiveFilter.type) {
                                FilterUIRegistry.getRenderer(currentActiveFilter)
                            }

                            // 注意：Renderer 内部的滑块回调应该调用 viewModel.handleEvent(PreviewFilter(newFilter))
                            // 这里假设 FilterUIRegistry 的实现已经对接了 ViewModel 或者提供了回调参数
                            // 如果你的 Renderer 是独立的，你需要确保它们能发送 PreviewFilter 事件
                            renderer.Content()

                            Spacer(Modifier.height(16.dp))

                            // --- 操作按钮 ---
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // 按钮 1: 修改当前步骤 (Update)
                                // 只有当选中的不是原图(Index > 0) 且不是 ViewFilter 时才可用
                                val canModify = pipelineState.selectedPipelineIndex > 0 && currentActiveFilter !is ViewFilter
                                Button(
                                    onClick = { viewModel.handleEvent(UpdateCurrentStep) },
                                    enabled = canModify,
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    contentPadding = PaddingValues(0.dp),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text("修改当前步骤", style = MaterialTheme.typography.labelMedium)
                                }

                                // 按钮 2: 添加新步骤 (Apply)
                                // 只要选择了有效滤镜即可添加
                                val canAdd = currentActiveFilter !is ViewFilter
                                Button(
                                    onClick = { viewModel.handleEvent(ApplyNewStep) },
                                    enabled = canAdd,
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    contentPadding = PaddingValues(0.dp),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text("应用(新增)", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
                1 -> { // 字符切割 Tab
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("规则设置功能开发中...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun InspectorTabs(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    TabRow(
        selectedTabIndex = selectedTab,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
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
        listOf("滤镜处理", "字符切割").forEachIndexed { index, title ->
            Tab(
                selected = selectedTab == index,
                onClick = { onTabSelected(index) },
                text = { Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium) },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}