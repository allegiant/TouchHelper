package org.eu.freex.tools.modules.image.presentation.features.filter.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.modules.image.domain.model.ViewFilter
import org.eu.freex.tools.modules.image.presentation.core.*
import org.eu.freex.tools.modules.image.presentation.features.filter.components.renderers.FilterUIRegistry

@Composable
fun InspectorPanel(
    modifier: Modifier = Modifier,
    uiState: ImageUiState,
) {
    val viewModel = LocalImageViewModel.current
    var selectedTab by rememberSaveable { mutableStateOf(0) }

    // 计算当前激活的滤镜对象：优先显示预览 -> 其次显示当前步骤 -> 最后默认原图
    val activeLayer = uiState.previewLayer ?: uiState.activeChain?.getActiveLayer(uiState.assets)
    val activeFilter = activeLayer?.activeFilter ?: ViewFilter

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .fillMaxHeight()
    ) {
        // 1. 顶部 Tab 栏
        InspectorTabs(selectedTab) { selectedTab = it }

        // 2. 内容区
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> { // --- 滤镜处理 Tab ---
                    Column(modifier = Modifier.fillMaxSize()) {

                        // A. 滤镜选择列表
                        FilterSelectionList(
                            modifier = Modifier.weight(1f),
                            currentFilter = activeFilter,
                            onFilterChange = { newFilter ->
                                viewModel.handleEvent(PreviewFilter(newFilter))
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
                            val renderer = remember(activeFilter::class) {
                                FilterUIRegistry.getRenderer(activeFilter)
                            }

                            renderer.Content(
                                filter = activeFilter,
                                onFilterChange = { newFilter ->
                                    viewModel.handleEvent(PreviewFilter(newFilter))
                                }
                            )

                            Spacer(Modifier.height(16.dp))

                            // --- 双按钮区域 ---
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val isOrigin = uiState.activeChain?.activeIndex == -1
                                val isPreviewing = uiState.previewLayer != null

                                // 1. 修改按钮 (Modify)
                                // 只有当前选中的不是原图，且正在调节参数时才可用
                                Button(
                                    onClick = { viewModel.handleEvent(UpdateCurrentStep) },
                                    enabled = !isOrigin && isPreviewing,
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

                                // 2. 应用按钮 (Apply/Add)
                                // 只要在预览，就可以作为新步骤添加
                                Button(
                                    onClick = { viewModel.handleEvent(ApplyNewStep) },
                                    enabled = isPreviewing,
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

                1 -> { // --- 切割识别 Tab ---
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("自动识别与切割", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Text("功能开发中...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            // 这里未来可以放 Segmentation 相关的参数调节
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InspectorTabs(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    SecondaryTabRow(
        selectedTabIndex = selectedTab,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
    ) {
        listOf("滤镜处理", "切割识别").forEachIndexed { index, title ->
            Tab(
                selected = selectedTab == index,
                onClick = { onTabSelected(index) },
                text = { Text(title, style = MaterialTheme.typography.titleSmall) }
            )
        }
    }
}