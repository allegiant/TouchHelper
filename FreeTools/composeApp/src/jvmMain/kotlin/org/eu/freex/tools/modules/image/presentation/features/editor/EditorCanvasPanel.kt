package org.eu.freex.tools.modules.image.presentation.features.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.eu.freex.tools.common.model.WorkbenchTab
import org.eu.freex.tools.modules.image.presentation.features.editor.behaviors.computeEditorBehavior
import org.eu.freex.tools.modules.image.presentation.viewmodel.EditorCanvasViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.SegmentationViewModel
import org.koin.compose.koinInject

// EditorCanvasPanel.kt
@Composable
fun EditorCanvasPanel(
    modifier: Modifier = Modifier,
    currentTab: WorkbenchTab, // 外部传入的 Tab
    editorViewModel: EditorCanvasViewModel = koinInject(),
    segmentationViewModel: SegmentationViewModel = koinInject()
) {
    // 1. 收集状态 (State Collection)
    val editorState by editorViewModel.uiState.collectAsState()
    val transformState = editorViewModel.transformState.collectAsState()
    val segmentationState by segmentationViewModel.uiState.collectAsState()

    // 2. [计算行为] 使用 remember 缓存计算结果
    // 只有当 tab, editorState 或 segmentationState 变化时，才重新计算 Behavior
    val behavior = remember(currentTab, editorState, segmentationState.project) {
        computeEditorBehavior(
            tab = currentTab,
            editorState = editorState,
            segmentationProject = segmentationState.project,
            editorVM = editorViewModel,
            segmentationVM = segmentationViewModel
        )
    }

    // 3. 渲染 UI (Pass-through)
    EditorCanvasContent(
        modifier = modifier,
        displayImage = editorState.displayImage,
        transformState = transformState,

        // 这里的代码完全不用变！
        cursorIcon = behavior.cursor,
        enableZoomPan = behavior.enableZoomPan,

        drawOnImage = { textMeasurer -> with(behavior) { onDraw(textMeasurer) } },
        overlayContent = { EditorBehaviorOverlay(behavior) },
        hoverContent = { img, screen, pixel, bounds -> EditorHoverOverlay(behavior, img, screen, pixel, bounds) },

        onTap = { x, y, color -> behavior.onTap(x, y, color) },
        onDoubleTap = { x, y -> behavior.onDoubleTap(x, y) },

        onTransform = { zoom, pan -> editorViewModel.updateTransform(zoom, pan) }
    )
}