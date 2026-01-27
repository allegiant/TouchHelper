package org.eu.freex.tools.modules.image.presentation.features.editor

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntRect
import org.koin.compose.koinInject
import org.eu.freex.tools.common.model.PickingType
import org.eu.freex.tools.common.model.WorkbenchTab
import org.eu.freex.tools.modules.image.presentation.features.editor.strategies.*
import org.eu.freex.tools.modules.image.presentation.viewmodel.EditorCanvasViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.SegmentationViewModel

/**
 * [EditorCanvasPanel] (Smart Component)
 * 职责：
 * 1. 注入 ViewModels 并收集状态。
 * 2. 策略工厂：根据当前 Tab 和状态，实例化对应的 [CanvasTabStrategy]。
 * 3. 优先级仲裁：区域框选 > 全局取色 > 业务 Tab。
 */
@Composable
fun EditorCanvasPanel(
    modifier: Modifier = Modifier,
    currentTab: WorkbenchTab,
    // 外部传入的 UI 状态 (如框选模式)
    isSelectingRegion: Boolean = false,
    onRegionSelectEnd: (IntRect) -> Unit = {},
    onRegionSelectCancel: () -> Unit = {},

    // 依赖注入
    editorViewModel: EditorCanvasViewModel = koinInject(),
    segmentationViewModel: SegmentationViewModel = koinInject()
) {
    val editorState by editorViewModel.uiState.collectAsState()
    val segmentationState by segmentationViewModel.uiState.collectAsState()

    // [核心] 策略工厂：根据状态动态切换策略
    // 使用 remember 缓存策略对象，依赖项变化时自动重建
    val strategy: CanvasTabStrategy = remember(
        currentTab,
        isSelectingRegion,
        editorState.pickingType,
        editorState.featurePoints,
        segmentationState
    ) {
        // === 优先级 1: 区域框选模式 ===
        // 此时画布被 RegionSelectorOverlay 接管，禁用缩放
        if (isSelectingRegion) {
            return@remember RegionSelectionStrategy(
                onRegionSelected = onRegionSelectEnd,
                onCancel = onRegionSelectCancel
            )
        }

        // === 优先级 2: 全局取色/取点模式 ===
        // 无论当前在哪个 Tab，一旦开启取色，立即切换行为
        if (editorState.pickingType != PickingType.NONE) {
            return@remember PickingStrategy(
                pickingType = editorState.pickingType,
                onPick = { offset, color ->
                    editorViewModel.onCanvasClick(offset, color)
                }
            )
        }

        // === 优先级 3: 标准业务 Tab ===
        when (currentTab) {
            WorkbenchTab.FILTER -> FilterStrategy(
                onClick = { x, y, color ->
                    // 滤镜模式下点击通常无操作，或者用于查看颜色信息
                    editorViewModel.onCanvasClick(Offset(x.toFloat(), y.toFloat()), color)
                }
            )

            WorkbenchTab.FEATURE -> FeatureStrategy(
                points = editorState.featurePoints,
                onAddPoint = { x, y, color ->
                    editorViewModel.addFeaturePoint(x, y, color)
                }
            )

            WorkbenchTab.SEGMENTATION -> SegmentationStrategy(
                project = segmentationState.project,
                selectedIndex = segmentationState.selectedIndex,
                onSelect = { x, y -> segmentationViewModel.onCanvasTap(x, y) },
                onDoubleTapAction = { segmentationViewModel.showLabelDialog() }
            )
        }
    }

    // 将决策好的策略和数据传给 Dumb Component 进行渲染
    EditorCanvasContent(
        modifier = modifier,
        displayImage = editorState.displayImage,
        strategy = strategy
    )
}