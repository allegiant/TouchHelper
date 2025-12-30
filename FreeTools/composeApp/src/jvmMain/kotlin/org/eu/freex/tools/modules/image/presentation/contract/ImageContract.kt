package org.eu.freex.tools.modules.image.presentation.contract

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import org.eu.freex.tools.model.AppFilter
import org.eu.freex.tools.model.GridParams
import org.eu.freex.tools.model.ViewFilter
import org.eu.freex.tools.model.WorkImage
import java.awt.image.BufferedImage

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
    val currentFilter: AppFilter = ViewFilter,

    // 【关键修改】isGridMode 默认为 false (智能模式)，或 true (网格模式)
    val isGridMode: Boolean = true,
    // 【关键修改】默认网格大小改为 100x100，避免太小看不见
    val gridParams: GridParams = GridParams(0, 0, 100, 100, 0, 0, 1, 1),

    val activeRects: List<Rect> = emptyList(),
    val segmentationResults: List<WorkImage> = emptyList(),
    val isScreenCropperVisible: Boolean = false,
    val fullScreenCapture: BufferedImage? = null,
    val isMappingDialogVisible: Boolean = false,
    val mappingBitmap: BufferedImage? = null,
) {
    val currentSourceImage: WorkImage? get() = sourceImages.getOrNull(selectedSourceIndex)

    val activeDisplayImage: WorkImage?
        get() {
            if (pipelineSteps.isNotEmpty() && selectedPipelineIndex > 0) {
                return pipelineSteps.getOrNull(selectedPipelineIndex - 1)
            }
            return currentSourceImage
        }

    val displayChain: List<WorkImage>
        get() {
            val list = mutableListOf<WorkImage>()
            currentSourceImage?.let { list.add(it.copy(label = "原图")) }
            list.addAll(pipelineSteps)
            return list
        }
}