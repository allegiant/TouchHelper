package org.eu.freex.tools.modules.image.presentation.features.editor

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntRect
import org.koin.compose.koinInject
import org.eu.freex.tools.common.model.WorkbenchTab
import org.eu.freex.tools.modules.image.presentation.features.editor.strategies.CanvasStrategyFactory
import org.eu.freex.tools.modules.image.presentation.features.editor.strategies.CanvasTabStrategy
import org.eu.freex.tools.modules.image.presentation.viewmodel.EditorCanvasViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.SegmentationViewModel

/**
 * [EditorCanvasPanel] (Smart Component)
 * 重构后：主要负责状态收集和依赖注入，逻辑决策委托给 Factory。
 */
@Composable
fun EditorCanvasPanel(
    modifier: Modifier = Modifier,
    currentTab: WorkbenchTab,
    isSelectingRegion: Boolean = false,
    onRegionSelectEnd: (IntRect) -> Unit = {},
    onRegionSelectCancel: () -> Unit = {},

    editorViewModel: EditorCanvasViewModel = koinInject(),
    segmentationViewModel: SegmentationViewModel = koinInject()
) {
    val editorState by editorViewModel.uiState.collectAsState()
    val segmentationState by segmentationViewModel.uiState.collectAsState()

    // [工厂模式] 重组策略
    // 注意：key 仅包含策略真正关心的字段，避免 State 中无关字段变化导致策略重建
    val strategy: CanvasTabStrategy = remember(
        currentTab,
        isSelectingRegion,
        editorState.pickingType,
        editorState.featurePoints,
        segmentationState.project,
        segmentationState.selectedIndex
    ) {
        CanvasStrategyFactory.create(
            currentTab = currentTab,
            isSelectingRegion = isSelectingRegion,
            pickingType = editorState.pickingType,
            featurePoints = editorState.featurePoints,
            segmentationProject = segmentationState.project,
            segmentationSelectedIndex = segmentationState.selectedIndex,

            // --- Callbacks ---
            onRegionSelectEnd = onRegionSelectEnd,
            onRegionSelectCancel = onRegionSelectCancel,
            onPick = { offset, color -> editorViewModel.onCanvasClick(offset, color) },
            onAddFeaturePoint = { x, y, color -> editorViewModel.addFeaturePoint(x, y, color) },
            onSegmentationTap = { x, y -> segmentationViewModel.onCanvasTap(x, y) },
            onSegmentationDoubleTap = { segmentationViewModel.showLabelDialog() }
        )
    }

    // [渲染] 传递状态和策略
    EditorCanvasContent(
        modifier = modifier,
        displayImage = editorState.displayImage,
        strategy = strategy,
        // (来自 Phase 1 的改动) 连接 View Model 的变换状态
        scale = editorState.scale,
        offset = editorState.pan,
        onTransform = { zoom, pan -> editorViewModel.updateTransform(zoom, pan) }
    )
}