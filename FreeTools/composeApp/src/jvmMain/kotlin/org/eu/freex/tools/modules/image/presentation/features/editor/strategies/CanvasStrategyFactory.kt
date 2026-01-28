package org.eu.freex.tools.modules.image.presentation.features.editor.strategies

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntRect
import org.eu.freex.tools.common.model.PickingType
import org.eu.freex.tools.common.model.WorkbenchTab
import org.eu.freex.tools.modules.image.domain.model.FeaturePoint
import org.eu.freex.tools.modules.image.domain.model.SegmentationProject

/**
 * [CanvasStrategyFactory]
 * 负责根据当前的 UI 状态和 Tab 类型，生产对应的画布交互策略。
 */
object CanvasStrategyFactory {

    fun create(
        // === 状态输入 ===
        currentTab: WorkbenchTab,
        isSelectingRegion: Boolean,
        pickingType: PickingType,
        featurePoints: List<FeaturePoint>,
        segmentationProject: SegmentationProject?,
        segmentationSelectedIndex: Int,

        // === 动作回调 (Action Callbacks) ===
        onRegionSelectEnd: (IntRect) -> Unit,
        onRegionSelectCancel: () -> Unit,
        onPick: (Offset, Color) -> Unit, // 用于取色/取点/通用点击
        onAddFeaturePoint: (Int, Int, Color) -> Unit,
        onSegmentationTap: (Int, Int) -> Unit,
        onSegmentationDoubleTap: () -> Unit
    ): CanvasTabStrategy {

        // === 优先级 1: 区域框选模式 ===
        if (isSelectingRegion) {
            return RegionSelectionStrategy(
                onRegionSelected = onRegionSelectEnd,
                onCancel = onRegionSelectCancel
            )
        }

        // === 优先级 2: 全局取色/取点模式 ===
        if (pickingType != PickingType.NONE) {
            return PickingStrategy(
                pickingType = pickingType,
                onPick = onPick
            )
        }

        // === 优先级 3: 标准业务 Tab ===
        return when (currentTab) {
            WorkbenchTab.FILTER -> FilterStrategy(
                onClick = { x, y, color ->
                    onPick(Offset(x.toFloat(), y.toFloat()), color)
                }
            )

            WorkbenchTab.FEATURE -> FeatureStrategy(
                points = featurePoints,
                onAddPoint = onAddFeaturePoint
            )

            WorkbenchTab.SEGMENTATION -> SegmentationStrategy(
                project = segmentationProject,
                selectedIndex = segmentationSelectedIndex,
                onSelect = onSegmentationTap,
                onDoubleTapAction = onSegmentationDoubleTap
            )
        }
    }
}