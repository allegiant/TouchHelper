package org.eu.freex.tools.modules.image.presentation.contract

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import org.eu.freex.tools.model.*
import java.awt.image.BufferedImage
import java.io.File

/**
 * MVI State: 唯一可信数据源
 */
data class ImageUiState(
    val binaryPreview: WorkImage? = null,
    // 资源
    val sourceImages: List<WorkImage> = emptyList(),
    val selectedSourceIndex: Int = -1,

    // 画布
    val mainScale: Float = 1f,
    val mainOffset: Offset = Offset.Zero,
    val hoverPixelPos: IntOffset? = null,
    val hoverColor: Color = Color.Transparent,

    // 流水线
    val pipelineSteps: List<WorkImage> = emptyList(),
    val selectedPipelineIndex: Int = 0,
    val isLoading: Boolean = false,

    // 右侧面板状态
    val rightPanelTabIndex: Int = 0, // 0:Filter, 1:Segmentation

    // 滤镜配置
    val currentFilter: ImageFilter = ViewFilter,
    val thresholdRange: ClosedFloatingPointRange<Float> = 0f..72f,
    val isRgbAvgEnabled: Boolean = true,

    // 规则配置
    val globalColorRules: List<ColorRule> = emptyList(),
    val currentScope: RuleScope = RuleScope.GLOBAL,

    // 切割配置
    val isGridMode: Boolean = false,
    val gridParams: GridParams = GridParams(0, 0, 15, 15, 0, 0, 1, 1),
    val activeRects: List<Rect> = emptyList(),
    val segmentationResults: List<WorkImage> = emptyList(),

    // 弹窗状态
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

    val activeColorRules: List<ColorRule> get() =
        if (currentScope == RuleScope.GLOBAL) globalColorRules else (currentSourceImage?.localColorRules ?: globalColorRules)
}

/**
 * MVI Event: 用户意图
 */
sealed class ImageUiEvent {
    // --- 1. 资源操作 ---
    data class LoadFile(val file: File) : ImageUiEvent()
    data class SelectSourceImage(val index: Int) : ImageUiEvent()
    data class RemoveSourceImage(val index: Int) : ImageUiEvent()
    object StartScreenCapture : ImageUiEvent()
    data class ConfirmScreenCrop(val image: BufferedImage) : ImageUiEvent()

    // --- 2. 画布操作 ---
    data class UpdateCanvasTransform(val scale: Float, val offset: Offset) : ImageUiEvent()
    data class HoverCanvas(val pos: IntOffset?, val color: Color) : ImageUiEvent()
    data class ColorPick(val hex: String) : ImageUiEvent()

    // --- 3. 流水线操作 ---
    data class SelectPipelineStep(val index: Int) : ImageUiEvent()
    data class DeletePipelineStep(val index: Int) : ImageUiEvent()
    data class ChangePanelTab(val index: Int) : ImageUiEvent()

    // --- 4. 滤镜配置 ---
    data class SelectFilter(val filter: ImageFilter) : ImageUiEvent()
    data class UpdateThreshold(val range: ClosedFloatingPointRange<Float>) : ImageUiEvent()
    data class ToggleRgbAvg(val enabled: Boolean) : ImageUiEvent()
    object ApplyCurrentFilter : ImageUiEvent()

    // --- 5. 切割配置 ---
    data class ToggleGridMode(val isGrid: Boolean) : ImageUiEvent()
    data class UpdateGridParams(val params: GridParams) : ImageUiEvent()
    object PerformSegmentation : ImageUiEvent()

    // --- 6. 规则管理 (【本次补全的部分】) ---
    data class UpdateColorRule(val id: Long, val bias: String) : ImageUiEvent()
    data class ToggleColorRule(val id: Long, val enabled: Boolean) : ImageUiEvent()
    data class RemoveColorRule(val id: Long) : ImageUiEvent()

    // --- 7. 弹窗与字库 (【本次补全的部分】) ---
    data class OpenMappingDialog(val rect: Rect) : ImageUiEvent()
    data class ConfirmMapping(val char: String) : ImageUiEvent()
    object DismissDialogs : ImageUiEvent()
}