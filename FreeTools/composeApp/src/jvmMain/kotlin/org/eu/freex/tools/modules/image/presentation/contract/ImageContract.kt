package org.eu.freex.tools.modules.image.presentation.contract

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import org.eu.freex.tools.model.*
import uniffi.touch_core.ImageFilter
import java.awt.image.BufferedImage
import java.io.File

data class ImageUiState(
    val binaryPreview: WorkImage? = null,
    val sourceImages: List<WorkImage> = emptyList(),
    val selectedSourceIndex: Int = -1,
    val mainScale: Float = 1f,
    val mainOffset: Offset = Offset.Zero,
    val hoverPixelPos: IntOffset? = null,
    val hoverColor: Color = Color.Transparent,
    val pipelineSteps: List<WorkImage> = emptyList(),
    val selectedPipelineIndex: Int = 0,
    val isLoading: Boolean = false,
    val rightPanelTabIndex: Int = 0,
    val currentFilter: ImageFilter = ImageFilter.View,
    val thresholdRange: ClosedFloatingPointRange<Float> = 0f..72f,
    val isRgbAvgEnabled: Boolean = true,

    // 【关键修改】isGridMode 默认为 false (智能模式)，或 true (网格模式)
    val isGridMode: Boolean = true,
    // 【关键修改】默认网格大小改为 100x100，避免太小看不见
    val gridParams: GridParams = GridParams(0, 0, 100, 100, 0, 0, 1, 1),

    val activeRects: List<Rect> = emptyList(),
    val segmentationResults: List<WorkImage> = emptyList(),
    val isScreenCropperVisible: Boolean = false,
    val fullScreenCapture: BufferedImage? = null,
    val isMappingDialogVisible: Boolean = false,
    val mappingBitmap: BufferedImage? = null
) {
    val currentSourceImage: WorkImage? get() = sourceImages.getOrNull(selectedSourceIndex)

    val activeDisplayImage: WorkImage? get() {
        if (pipelineSteps.isNotEmpty() && selectedPipelineIndex > 0) {
            return pipelineSteps.getOrNull(selectedPipelineIndex - 1)
        }
        return currentSourceImage
    }

    val displayChain: List<WorkImage> get() {
        val list = mutableListOf<WorkImage>()
        currentSourceImage?.let { list.add(it.copy(label = "原图")) }
        list.addAll(pipelineSteps)
        return list
    }
}

sealed class ImageUiEvent {
    // --- 资源 ---
    data class LoadFile(val file: File) : ImageUiEvent()
    data class SelectSourceImage(val index: Int) : ImageUiEvent()
    data class RemoveSourceImage(val index: Int) : ImageUiEvent()
    object StartScreenCapture : ImageUiEvent()
    data class ConfirmScreenCrop(val image: BufferedImage) : ImageUiEvent()

    // --- 滤镜 ---
    object ApplyCurrentFilter : ImageUiEvent()
    object ModifyCurrentStep : ImageUiEvent()
    data class SelectFilter(val filter: ImageFilter) : ImageUiEvent()
    data class SelectPipelineStep(val index: Int) : ImageUiEvent()
    data class DeletePipelineStep(val index: Int) : ImageUiEvent()
    data class UpdateThreshold(val range: ClosedFloatingPointRange<Float>) : ImageUiEvent()
    data class ToggleRgbAvg(val enabled: Boolean) : ImageUiEvent()

    // --- 规则与切割 ---
    object PerformSegmentation : ImageUiEvent()

    data class UpdateGridParams(val params: GridParams) : ImageUiEvent()
    data class ToggleGridMode(val isGrid: Boolean) : ImageUiEvent()

    // --- 画布交互 ---
    data class UpdateCanvasTransform(val scale: Float, val offset: Offset) : ImageUiEvent()
    data class HoverCanvas(val pixelPos: Offset, val color: Color) : ImageUiEvent()
    data class ColorPick(val hex: String) : ImageUiEvent()
    data class ChangePanelTab(val index: Int) : ImageUiEvent()

    // --- 弹窗 ---
    object DismissDialogs : ImageUiEvent()
    data class OpenMappingDialog(val rect: Rect) : ImageUiEvent()
    data class ConfirmMapping(val char: String) : ImageUiEvent()
}