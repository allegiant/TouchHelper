package org.eu.freex.tools.modules.image.presentation.features.filter.components

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
    onTabChange: (WorkbenchTab) -> Unit
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
            Box(modifier = Modifier.weight(1f)) {
                when (currentTab) {
                    WorkbenchTab.FILTER -> {
                        FilterTabContent()
                    }

                    WorkbenchTab.SEGMENTATION -> {
                        SegmentationTabContent()
                    }
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

    // 1. 计算当前的基准滤镜 (Base Filter)
    // 如果选中了某个滤镜步骤，就以该步骤的参数为基准；如果是原图或空，则使用默认 ViewFilter
    val baseFilter = remember(pipeline?.activeIndex, pipeline?.steps) {
        if (pipeline != null && pipeline.activeIndex != -1) {
            val activeLayer = pipeline.steps.getOrNull(pipeline.activeIndex)
            (activeLayer?.config as? LayerConfig.Filter)?.filter ?: ViewFilter
        } else {
            ViewFilter
        }
    }

    // 2. 本地编辑状态：用于 UI 显示和参数调节
    // 当 baseFilter 变化（比如用户切换了步骤）时，重置 editingFilter
    var editingFilter by remember(baseFilter) { mutableStateOf(baseFilter) }

    // 3. 实时预览逻辑
    // 当 editingFilter 发生变化且与 baseFilter 不同时，触发预览
    LaunchedEffect(editingFilter) {
        if (editingFilter != baseFilter) {
            viewModel.onFilterPreviewChange(editingFilter)
        }
    }

    // 当组件销毁或切换 Tab 时，应该清除预览 (可选，ViewModel 内部通常也会处理)
    // DisposableEffect(Unit) { onDispose { viewModel.onFilterPreviewChange(baseFilter) } }

    Column(modifier = Modifier.fillMaxSize()) {
        // A. 滤镜选择列表
        // 注意：FilterSelectionList 需要支持“选择新类型”来替换当前的 editingFilter
        FilterSelectionList(
            modifier = Modifier.weight(1f),
            currentFilter = editingFilter,
            onFilterChange = { newFilter ->
                editingFilter = newFilter
            }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // B. 底部参数与按钮区
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(12.dp)
        ) {
            Text(
                "参数调节",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // 动态渲染参数 UI
            val renderer = remember(editingFilter::class) {
                FilterUIRegistry.getRenderer(editingFilter)
            }

            renderer.Content(
                filter = editingFilter,
                onFilterChange = { newFilter -> editingFilter = newFilter }
            )

            Spacer(Modifier.height(16.dp))

            // --- 双按钮区域 ---
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val isOrigin = pipeline?.activeIndex == -1
                // 只有选中了具体的滤镜步骤(非原图)，才允许“修改步骤”
                val canModify = !isOrigin

                // 1. 修改步骤
                Button(
                    onClick = {
                        // 确认修改，提交到 ViewModel
                        viewModel.onFilterValueConfirmed(editingFilter)
                    },
                    enabled = canModify,
                    modifier = Modifier.weight(1f).height(40.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("修改步骤", style = MaterialTheme.typography.labelMedium)
                }

                // 2. 添加步骤
                Button(
                    onClick = {
                        // 添加新步骤到流水线
                        viewModel.addFilter(editingFilter)
                    },
                    enabled = true,
                    modifier = Modifier.weight(1f).height(40.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("添加步骤", style = MaterialTheme.typography.labelMedium)
                }
            }
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
            text = { Text("滤镜处理", style = MaterialTheme.typography.titleSmall) }
        )
        Tab(
            selected = currentTab == WorkbenchTab.SEGMENTATION,
            onClick = { onSwitch(WorkbenchTab.SEGMENTATION) },
            text = { Text("切割识别", style = MaterialTheme.typography.titleSmall) }
        )
    }
}