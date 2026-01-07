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
import org.eu.freex.tools.modules.image.domain.model.BinarizationFilter
import org.eu.freex.tools.modules.image.domain.model.BinarizationMode
import org.eu.freex.tools.modules.image.domain.model.ImageFilter
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

    // 1. 获取当前步骤的“真实”数据源（不包含预览状态）
    // 只有当 pipeline 切换或 assets 变化时，这里才会变
    val activeLayer = uiState.activeChain?.getActiveLayer(uiState.assets)
    val baseFilter = activeLayer?.activeFilter ?: ViewFilter

    // 2. 本地编辑状态：用于 UI 显示和参数调节
    // 当底层的 baseFilter 变化（比如切换了步骤）时，重置本地状态
    var editingFilter by remember(activeLayer?.id, baseFilter) { mutableStateOf(baseFilter) }

    // 3. 核心逻辑：处理滤镜更新
    fun updateEditingFilter(newFilter: ImageFilter) {
        editingFilter = newFilter

        // 【关键需求】例外判断：只有 二值化 + 手动模式 需要实时预览
        val isManualBinarization = (newFilter is BinarizationFilter && newFilter.mode == BinarizationMode.MANUAL)

        if (isManualBinarization) {
            viewModel.handleEvent(PreviewFilter(newFilter))
        } else {
            // 其他情况，如果当前有遗留的预览（比如刚从二值化切过来），需要取消预览，恢复显示原图
            if (uiState.previewLayer != null) {
                viewModel.handleEvent(CancelPreview)
            }
        }
    }

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
                            currentFilter = editingFilter, // 使用本地状态
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
                                filter = editingFilter, // 使用本地状态
                                onFilterChange = { updateEditingFilter(it) }
                            )

                            Spacer(Modifier.height(16.dp))

                            // --- 双按钮区域 ---
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val isOrigin = uiState.activeChain?.activeIndex == -1
                                // 修改按钮逻辑：只要不是在原图上（即在某个步骤上），就可以修改当前步骤
                                // 预览状态已解耦，所以不需要依赖 isPreviewing
                                val canModify = !isOrigin

                                // 1. 修改按钮 (Modify)
                                Button(
                                    onClick = { viewModel.handleEvent(UpdateFilterStep(editingFilter)) },
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

                                // 2. 应用按钮 (Add)
                                // 总是可用，将当前配置添加为新步骤
                                Button(
                                    onClick = { viewModel.handleEvent(ApplyFilterStep(editingFilter)) },
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

                1 -> { // --- 切割识别 Tab ---
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("自动识别与切割", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Text("功能开发中...", color = MaterialTheme.colorScheme.onSurfaceVariant)
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