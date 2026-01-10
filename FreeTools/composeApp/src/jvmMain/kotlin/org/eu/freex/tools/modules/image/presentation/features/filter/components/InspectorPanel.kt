package org.eu.freex.tools.modules.image.presentation.features.filter.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.modules.image.domain.model.BinarizationFilter
import org.eu.freex.tools.modules.image.domain.model.BinarizationMode
import org.eu.freex.tools.modules.image.domain.model.ImageFilter
import org.eu.freex.tools.modules.image.domain.model.ViewFilter
import org.eu.freex.tools.modules.image.presentation.core.*
import org.eu.freex.tools.modules.image.presentation.features.filter.components.renderers.FilterUIRegistry
import org.eu.freex.tools.modules.image.presentation.features.segmentation.SegmentationPanel

@Composable
fun InspectorPanel(
    modifier: Modifier = Modifier,
    uiState: ImageUiState,
    onEvent: (ImageUiEvent) -> Unit
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            // 1. 顶部 Tab 栏 (状态来自 UI State)
            InspectorTabs(
                currentTab = uiState.activeTab,
                onSwitch = { onEvent(SwitchTab(it)) }
            )

            // 2. 根据 Tab 显示不同内容
            Box(modifier = Modifier.weight(1f)) {
                when (uiState.activeTab) {
                    WorkbenchTab.FILTER -> {
                        FilterTabContent(
                            uiState = uiState,
                            onEvent = onEvent
                        )
                    }
                    WorkbenchTab.SEGMENTATION -> {
                        SegmentationTabContent(
                            uiState = uiState,
                            onEvent = onEvent
                        )
                    }
                }
            }
        }
    }
}

/**
 * 滤镜处理 Tab 的内容
 * (重构：将原有的 InspectorPanel 核心逻辑移到这里)
 */
@Composable
private fun FilterTabContent(
    uiState: ImageUiState,
    onEvent: (ImageUiEvent) -> Unit
) {
    // 1. 获取当前步骤的“真实”数据源
    val activeLayer = uiState.activeChain?.getActiveLayer(uiState.assets)
    val baseFilter = activeLayer?.activeFilter ?: ViewFilter

    // 2. 本地编辑状态：用于 UI 显示和参数调节
    // 当底层的 baseFilter 变化（比如切换了步骤）时，重置本地状态
    var editingFilter by remember(activeLayer?.id, baseFilter) { mutableStateOf(baseFilter) }

    // 3. 内部逻辑：处理滤镜更新
    fun updateEditingFilter(newFilter: ImageFilter) {
        editingFilter = newFilter

        // 实时预览逻辑
        val isManualBinarization = (newFilter is BinarizationFilter && newFilter.mode == BinarizationMode.MANUAL)

        if (isManualBinarization) {
            onEvent(PreviewFilter(newFilter))
        } else {
            if (uiState.previewLayer != null) {
                onEvent(CancelPreview)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // A. 滤镜选择列表
        FilterSelectionList(
            modifier = Modifier.weight(1f),
            currentFilter = editingFilter,
            onFilterChange = { updateEditingFilter(it) }
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
                onFilterChange = { newFilter -> updateEditingFilter(newFilter) }
            )

            Spacer(Modifier.height(16.dp))

            // --- 双按钮区域 ---
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val isOrigin = uiState.activeChain?.activeIndex == -1
                val canModify = !isOrigin

                // 1. 修改步骤
                Button(
                    onClick = { onEvent(UpdateFilterStep(editingFilter)) },
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
                    onClick = { onEvent(ApplyFilterStep(editingFilter)) },
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
    uiState: ImageUiState,
    onEvent: (ImageUiEvent) -> Unit
) {
    val project = uiState.segmentationProject

    if (project != null) {
        SegmentationPanel(
            modifier = Modifier.fillMaxSize(),
            // 传递数据 (Domain)
            config = project.config,
            results = project.results,
            labels = project.labels,
            // 传递交互状态 (UI State)
            interaction = uiState.segmentationInteraction,
            // 传递图片源 (BufferedImage)
            sourceImage = uiState.displayImage?.image,

            // 事件代理
            onConfigChange = { onEvent(UpdateSegmentationConfig(it)) },
            onSelectChar = { onEvent(SelectChar(it)) },
            onSubmitLabel = { onEvent(SubmitLabelAndNext(it)) },
            onStopLabeling = { onEvent(StopLabeling) }
        )
    } else {
        // 如果数据未初始化 (理论上 ViewModel 在切换 Tab 时会初始化)
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