/* file: EditorCanvasPanel.kt */
package org.eu.freex.tools.modules.image.presentation.features.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.eu.freex.tools.common.model.PickingType
import org.eu.freex.tools.common.model.WorkbenchTab
import org.eu.freex.tools.common.utils.toComposeColor
import org.eu.freex.tools.modules.image.presentation.features.editor.components.DefaultHoverInfoOverlay
import org.eu.freex.tools.modules.image.presentation.features.editor.components.MagnifierOverlay
import org.eu.freex.tools.modules.image.presentation.features.feature.components.RegionSelectorOverlay
import org.eu.freex.tools.modules.image.presentation.features.segmentation.components.drawSegmentationOverlay
import org.eu.freex.tools.modules.image.presentation.viewmodel.EditorCanvasViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.SegmentationViewModel
import org.koin.compose.koinInject
import java.awt.Cursor

@Composable
fun EditorCanvasPanel(
    modifier: Modifier = Modifier,
    currentTab: WorkbenchTab,
    editorViewModel: EditorCanvasViewModel = koinInject(),
    segmentationViewModel: SegmentationViewModel = koinInject()
) {
    // 1. Sync Tab
    LaunchedEffect(currentTab) {
        editorViewModel.setTab(currentTab)
    }

    // 2. Collect State
    val uiState by editorViewModel.uiState.collectAsState()
    val transformState = editorViewModel.transformState.collectAsState()
    val segmentationState by segmentationViewModel.uiState.collectAsState()

    // 3. Render
    EditorCanvasContent(
        modifier = modifier,
        displayImage = uiState.displayImage,
        transformState = transformState,

        // === 行为配置 ===
        // 只有裁剪时禁用缩放
        enableZoomPan = !uiState.isCropping,

        // 光标逻辑：工具优先，其次是特定 Tab 的默认习惯
        cursorIcon = when {
            uiState.isCropping -> PointerIcon(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR))
            uiState.pickingType != PickingType.NONE -> PointerIcon(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR))
            uiState.currentTab == WorkbenchTab.FEATURE -> PointerIcon(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR))
            else -> PointerIcon.Default
        },

        // === 画布绘制 (Context) ===
        // 这里的绘制只依赖于 Tab，不被 pickingType 打断
        drawOnImage = { textMeasurer ->
            when (uiState.currentTab) {
                WorkbenchTab.FEATURE -> {
                    uiState.featurePoints.forEach { point ->
                        val pointColor = point.colorHex.toComposeColor()
                        drawCircle(color = pointColor, radius = 10f, center = Offset(point.x.toFloat(), point.y.toFloat()))
                        drawCircle(color = Color.White, radius = 10f, center = Offset(point.x.toFloat(), point.y.toFloat()), style = Stroke(width = 2f))
                    }
                }
                WorkbenchTab.SEGMENTATION -> {
                    drawSegmentationOverlay(
                        project = segmentationState.project,
                        textMeasurer = textMeasurer,
                        selectedIndex = -1
                    )
                }
                else -> {}
            }
        },

        // === 点击交互 (Action) ===
        // 逻辑：Crop > Picking Tool > Tab Default Action
        onTap = { x, y, color ->
            if (uiState.pickingType != PickingType.NONE) {
                // 处理工具点击
                when (uiState.pickingType) {
                    PickingType.COLOR -> editorViewModel.pickColor(color)
                    PickingType.POINT -> editorViewModel.pickPoint(x, y)
                    else -> {}
                }
            } else {
                // 处理 Tab 默认点击
                when (uiState.currentTab) {
                    WorkbenchTab.FEATURE -> editorViewModel.addFeaturePoint(x, y, color)
                    WorkbenchTab.SEGMENTATION -> segmentationViewModel.onCanvasTap(x, y)
                    WorkbenchTab.FILTER -> editorViewModel.pickColor(color) // 滤镜页面默认点一下取色
                    else -> {}
                }
            }
        },

        onDoubleTap = { x, y ->
            if (uiState.currentTab == WorkbenchTab.SEGMENTATION) {
                segmentationViewModel.showLabelDialog()
            }
        },

        onTransform = { zoom, pan -> editorViewModel.updateTransform(zoom, pan) },

        // === 全屏覆盖 (Overlay) ===
        overlayContent = {
            if (uiState.isCropping) {
                RegionSelectorOverlay(
                    modifier = Modifier.matchParentSize().zIndex(100f),
                    onRegionSelected = { editorViewModel.confirmCrop(it) },
                    onCancel = { editorViewModel.exitCropMode() }
                )
            }
        },

        // === 悬浮提示 (Hover) ===
        hoverContent = { img, screenPos, pixelPos, inBounds ->
            val showMagnifier = uiState.pickingType != PickingType.NONE
                    || uiState.currentTab == WorkbenchTab.FEATURE // 特征点模式也可能想要放大镜辅助对准？

            // 如果处于取色/取点模式，或者某些需要精细操作的 Tab，显示放大镜
            if (showMagnifier && inBounds) {
                Box(modifier = Modifier.zIndex(200f)) {
                    MagnifierOverlay(
                        sourceImage = img,
                        centerPixel = pixelPos,
                        screenPos = screenPos
                    )
                }
            } else {
                // 默认信息
                DefaultHoverInfoOverlay(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                        .zIndex(190f),
                    pixelPos = pixelPos,
                    inBounds = inBounds,
                    image = img
                )
            }
        }
    )
}