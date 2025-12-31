package org.eu.freex.tools.modules.image.presentation.contract

import androidx.compose.ui.geometry.Rect
import org.eu.freex.tools.modules.image.domain.model.AppFilter
import org.eu.freex.tools.modules.image.domain.model.GridParams
import org.eu.freex.tools.modules.image.domain.model.ViewFilter
import org.eu.freex.tools.modules.image.domain.model.WorkImage
import java.awt.image.BufferedImage

// --- 项目源状态 ---
data class ProjectState(
    val sourceImages: List<WorkImage> = emptyList(),
    val selectedSourceIndex: Int = -1,
) {
    val currentSourceImage: WorkImage?
        get() = sourceImages.getOrNull(selectedSourceIndex)
}

// --- 【核心重构】编辑草稿状态 ---
// 这个状态专门用于持有当前正在调整但尚未“应用”或“保存”的参数和预览图
data class DraftState(
    val activeFilter: AppFilter = ViewFilter, // 当前属性面板应该显示的滤镜参数
    val previewImage: WorkImage? = null,      // 经过该滤镜处理后的预览图 (用于画布显示)
    val baseImage: WorkImage? = null          // 该滤镜是基于哪张图处理的 (用于参数变化时重新计算)
)

// --- 流水线状态 ---
data class PipelineState(
    val pipelineSteps: List<WorkImage> = emptyList(), // 已提交的步骤列表
    val selectedPipelineIndex: Int = 0,               // 0 代表原图，1..N 代表步骤
    val draft: DraftState = DraftState()              // 当前的编辑区域状态
)

// --- 分割/OCR 状态 ---
data class SegmentationState(
    val isGridMode: Boolean = true,
    val gridParams: GridParams = GridParams(0, 0, 100, 100, 0, 0, 1, 1),
    val activeRects: List<Rect> = emptyList(),
    val segmentationResults: List<WorkImage> = emptyList()
)

// --- UI 交互状态 ---
data class UiInteractionState(
    val isLoading: Boolean = false,
    val rightPanelTabIndex: Int = 0,
    val isScreenCropperVisible: Boolean = false,
    val fullScreenCapture: BufferedImage? = null,
    val isMappingDialogVisible: Boolean = false,
    val mappingBitmap: BufferedImage? = null
)

// --- 总 UI 状态 ---
data class ImageUiState(
    val project: ProjectState = ProjectState(),
    val pipeline: PipelineState = PipelineState(),
    val segmentation: SegmentationState = SegmentationState(),
    val ui: UiInteractionState = UiInteractionState(),
) {
    /**
     * 画布显示逻辑：
     * 1. 如果有草稿预览图 (Draft Preview)，优先显示草稿（实时反馈调节结果）
     * 2. 否则显示当前选中的步骤
     * 3. 最后兜底原图
     */
    val activeDisplayImage: WorkImage?
        get() = when {
            pipeline.draft.previewImage != null -> pipeline.draft.previewImage
            pipeline.selectedPipelineIndex > 0 -> pipeline.pipelineSteps.getOrNull(pipeline.selectedPipelineIndex - 1)
            else -> project.currentSourceImage?.copy(label = "原图")
        }

    // 底部历史条显示链
    val displayChain: List<WorkImage>
        get() = buildList {
            project.currentSourceImage?.let { add(it.copy(label = "原图")) }
            addAll(pipeline.pipelineSteps)
        }
}

// 辅助扩展方法：获取当前选中步骤的前一步图像（作为下一次处理的输入）
fun ImageUiState.getPreviousImageForProcessing(): WorkImage? {
    val index = pipeline.selectedPipelineIndex
    return if (index == 0) {
        project.currentSourceImage
    } else {
        pipeline.pipelineSteps.getOrNull(index - 1)
    }
}