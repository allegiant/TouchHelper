package org.eu.freex.tools.modules.image.presentation.features.filter.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.common.components.HelpTooltip
import org.eu.freex.tools.common.model.WorkbenchTab
import org.eu.freex.tools.modules.image.domain.model.LayerConfig
import org.eu.freex.tools.modules.image.domain.model.ViewFilter
import org.eu.freex.tools.modules.image.presentation.features.filter.components.renderers.FilterUIRegistry
import org.eu.freex.tools.modules.image.presentation.features.segmentation.SegmentationPanel
import org.eu.freex.tools.modules.image.presentation.viewmodel.PipelineViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.SegmentationViewModel
import org.koin.compose.koinInject

@Composable
fun InspectorPanel(
    modifier: Modifier = Modifier,
    currentTab: WorkbenchTab,
    onTabChange: (WorkbenchTab) -> Unit,
) {

    Surface(
        modifier = modifier.fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            // 2. 顶部 Tab 栏
            InspectorTabs(
                currentTab = currentTab,
                onSwitch = onTabChange
            )

            // 3. 根据 Tab 显示不同内容
            Box(modifier = Modifier.weight(1f).padding(8.dp)) {
                when (currentTab) {
                    WorkbenchTab.FILTER -> {
                        FilterTabContent()
                    }

                    WorkbenchTab.SEGMENTATION -> {
                        SegmentationTabContent()
                    }

                    else -> {}
                }
            }
        }
    }
}


/**
 * 滤镜处理 Tab 的内容
 */
@Composable
private fun FilterTabContent(
    viewModel: PipelineViewModel = koinInject()
) {
    val uiState by viewModel.uiState.collectAsState()
    val pipeline = uiState.pipeline

    val baseFilter = remember(pipeline?.activeIndex, pipeline?.steps) {
        if (pipeline != null && pipeline.activeIndex != -1) {
            val activeLayer = pipeline.steps.getOrNull(pipeline.activeIndex)
            (activeLayer?.config as? LayerConfig.Filter)?.filter ?: ViewFilter
        } else {
            ViewFilter
        }
    }

    var editingFilter by remember(baseFilter) { mutableStateOf(baseFilter) }
    // 如果没有选中任何步骤，默认展开列表；否则默认收起列表，专注调参
    var isSelectionExpanded by remember { mutableStateOf(pipeline?.activeIndex == -1) }

    LaunchedEffect(editingFilter) {
        if (editingFilter != baseFilter) {
            viewModel.onFilterPreviewChange(editingFilter)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // --- 1. 统一滚动区域 (包含列表和参数) ---
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // A. 折叠式滤镜列表
            FilterSelectionSection(
                currentFilter = editingFilter,
                isExpanded = isSelectionExpanded,
                onExpandChange = { isSelectionExpanded = it },
                onFilterChange = {
                    editingFilter = it
                    isSelectionExpanded = false // 选中后自动收起
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // B. 参数调节区
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "参数调节",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    // 当前滤镜状态标签
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            editingFilter.name,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                val renderer = remember(editingFilter::class) {
                    FilterUIRegistry.getRenderer(editingFilter)
                }
                renderer.Content(
                    filter = editingFilter,
                    onFilterChange = { newFilter -> editingFilter = newFilter }
                )
            }
        }

        // --- 2. 固定的操作按钮区 ---
        Surface(
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            FilterActionButtons(
                canModify = pipeline?.activeIndex != -1,
                onModify = { viewModel.onFilterValueConfirmed(editingFilter) },
                onAdd = { viewModel.addFilter(editingFilter) }
            )
        }
    }
}

/**
 * 切割识别 Tab 的内容
 */
@Composable
private fun SegmentationTabContent(
    viewModel: SegmentationViewModel = koinInject()
) {
    val state by viewModel.uiState.collectAsState()
    val project = state.project

    if (project != null) {
        // [新架构] 直接调用无参的 SegmentationPanel，它会自动注入所需的 ViewModels
        SegmentationPanel(modifier = Modifier.fillMaxSize())
    } else {
        // 如果数据未初始化 (理论上 ViewModel 初始化时会加载或状态为空)
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(8.dp))
            Text("正在初始化切割模块...", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InspectorTabs(
    currentTab: WorkbenchTab,
    onSwitch: (WorkbenchTab) -> Unit
) {
    SecondaryTabRow(
        selectedTabIndex = currentTab.ordinal,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
    ) {
        Tab(
            selected = currentTab == WorkbenchTab.FILTER,
            onClick = { onSwitch(WorkbenchTab.FILTER) },
            text = {
                HelpTooltip(text = "滤镜处理") {
                    FilterUsageGuide()
                }
            }
        )

        // --- 切割识别 Tab ---
        Tab(
            selected = currentTab == WorkbenchTab.SEGMENTATION,
            onClick = { onSwitch(WorkbenchTab.SEGMENTATION) },
            text = { Text("切割识别", style = MaterialTheme.typography.titleSmall) }
        )
    }
}