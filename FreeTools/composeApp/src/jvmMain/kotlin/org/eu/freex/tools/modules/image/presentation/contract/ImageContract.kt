package org.eu.freex.tools.modules.image.presentation.contract

import androidx.compose.ui.geometry.Rect
import org.eu.freex.tools.modules.image.domain.model.GridParams
import org.eu.freex.tools.modules.image.domain.model.WorkImage
import java.awt.image.BufferedImage


// --- 1. 定义子状态 (Domain Domains) ---

data class ProjectState(
    val sourceImages: List<WorkImage> = emptyList(),
    val selectedSourceIndex: Int = -1,
) {
    val currentSourceImage: WorkImage?
        get() = sourceImages.getOrNull(selectedSourceIndex)
}

// 1. 新增：专注于处理流程的状态
data class PipelineState(
    val pipelineSteps: List<WorkImage> = emptyList(),
    val selectedPipelineIndex: Int = 0,
    val currentImage: WorkImage? = null
)

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
    val project: ProjectState = ProjectState(),
    val pipeline: PipelineState = PipelineState(),
    val segmentation: SegmentationState = SegmentationState(),
    val ui: UiInteractionState = UiInteractionState(),
) {
    val activeDisplayImage: WorkImage?
        get() = when {
            // 1. 预览模式 (优先级最高)
            pipeline.currentImage != null -> pipeline.currentImage
            // 2. 历史步骤
            pipeline.selectedPipelineIndex > 0 -> pipeline.pipelineSteps.getOrNull(pipeline.selectedPipelineIndex - 1)
            // 3. 原图 (兜底)
            // 【优化】这里做一个浅拷贝，把标签改成 "原图"，与底部列表 displayChain 保持一致
            else -> project.currentSourceImage?.copy(label = "原图")
        }
    val displayChain: List<WorkImage>
        get() = buildList {
            project.currentSourceImage?.let { add(it.copy(label = "原图")) }
            addAll(pipeline.pipelineSteps)
        }
}

