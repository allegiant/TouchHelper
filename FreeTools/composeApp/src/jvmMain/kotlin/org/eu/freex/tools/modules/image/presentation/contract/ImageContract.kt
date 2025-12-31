package org.eu.freex.tools.modules.image.presentation.contract

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import org.eu.freex.tools.modules.image.domain.model.AppFilter
import org.eu.freex.tools.modules.image.domain.model.GridParams
import org.eu.freex.tools.modules.image.domain.model.ViewFilter
import org.eu.freex.tools.modules.image.domain.model.WorkImage
import java.awt.image.BufferedImage


// --- 1. 定义子状态 (Domain Domains) ---

data class ProjectState(
    val sourceImages: List<WorkImage> = emptyList(),
    val selectedSourceIndex: Int = -1,
    val pipelineSteps: List<WorkImage> = emptyList(),
    val selectedPipelineIndex: Int = 0,
    val currentFilter: AppFilter = ViewFilter
) {
    val currentSourceImage: WorkImage?
        get() = sourceImages.getOrNull(selectedSourceIndex)

    val activeDisplayImage: WorkImage?
        get() = if (selectedPipelineIndex == 0) currentSourceImage
        else pipelineSteps.getOrNull(selectedPipelineIndex - 1)

    val displayChain: List<WorkImage>
        get() {
            val list = mutableListOf<WorkImage>()
            currentSourceImage?.let { list.add(it.copy(label = "原图")) }
            list.addAll(pipelineSteps)
            return list
        }
}

data class SegmentationState(
    val isGridMode: Boolean = true,
    val gridParams: GridParams = GridParams(0, 0, 100, 100, 0, 0, 1, 1),
    val activeRects: List<Rect> = emptyList(),
    val segmentationResults: List<WorkImage> = emptyList()
)

data class UiInteractionState(
    val isLoading: Boolean = false,
    val rightPanelTabIndex: Int = 0,
    val isScreenCropperVisible: Boolean = false,
    val fullScreenCapture: BufferedImage? = null,
    val isMappingDialogVisible: Boolean = false,
    val mappingBitmap: BufferedImage? = null
)

data class ImageUiState(
// 真实的数据源
    val project: ProjectState = ProjectState(),
    val segmentation: SegmentationState = SegmentationState(),
    val ui: UiInteractionState = UiInteractionState(),
)

