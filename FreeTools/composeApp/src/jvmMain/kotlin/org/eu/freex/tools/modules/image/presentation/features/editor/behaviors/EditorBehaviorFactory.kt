package org.eu.freex.tools.modules.image.presentation.features.editor.behaviors

import androidx.compose.ui.geometry.Offset
import org.eu.freex.tools.common.model.PickingType
import org.eu.freex.tools.common.model.WorkbenchTab
import org.eu.freex.tools.modules.image.domain.model.SegmentationProject
import org.eu.freex.tools.modules.image.presentation.viewmodel.EditorCanvasUiState
import org.eu.freex.tools.modules.image.presentation.viewmodel.EditorCanvasViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.SegmentationViewModel

/**
 * [核心逻辑] 纯函数：根据当前所有的应用状态，决定画布应该表现出什么行为。
 * * @param tab 当前选中的 Tab (来自 Workbench)
 * @param editorState 画布自身状态 (来自 EditorCanvasViewModel)
 * @param segmentationProject 分割数据 (来自 SegmentationViewModel)
 * @param editorVM 编辑器 VM (用于绑定回调动作)
 * @param segmentationVM 分割 VM (用于绑定回调动作)
 */
fun computeEditorBehavior(
    tab: WorkbenchTab,
    editorState: EditorCanvasUiState,
    segmentationProject: SegmentationProject?,
    editorVM: EditorCanvasViewModel,
    segmentationVM: SegmentationViewModel
): EditorBehavior {

    // 优先级逻辑依然在这里，但现在它不依赖于任何特定的 ViewModel 生命周期
    return when {
        // 1. [高优先级] 正在框选 (Editor 内部状态)
        editorState.isSelectingRegion -> RegionSelectingBehavior(
            onEnd = { rect -> editorVM.confirmCrop(rect) },
            onCancel = { editorVM.exitCropMode() }
        )

        // 2. [高优先级] 正在取色 (Editor 内部状态)
        editorState.pickingType != PickingType.NONE -> PickingBehavior(
            type = editorState.pickingType,
            onPick = { x, y, color -> editorVM.onCanvasClick(Offset(x.toFloat(), y.toFloat()), color) } // 注意参数适配
        )

        // 3. [业务 Tab] 特征点
        tab == WorkbenchTab.FEATURE -> FeatureBehavior(
            points = editorState.featurePoints,
            onAddPoint = { x, y, color -> editorVM.addFeaturePoint(x, y, color) }
        )

        // 4. [业务 Tab] 分割识别
        tab == WorkbenchTab.SEGMENTATION -> SegmentationBehavior(
            project = segmentationProject,
            selectedIndex = -1, // 假设没有选中态
            onSelect = { x, y -> segmentationVM.onCanvasTap(x, y) }, // 假设 segmentationVM 有此方法
            onShowDialog = { segmentationVM.showLabelDialog() }
        )

        tab == WorkbenchTab.FILTER -> FilterBehavior(
            onTapAction = { x, y, color ->
                // 调用 VM 的点击逻辑
                editorVM.onCanvasClick(Offset(x.toFloat(), y.toFloat()), color)
            }
        )

        // 5. 默认
        else -> DefaultBehavior
    }
}