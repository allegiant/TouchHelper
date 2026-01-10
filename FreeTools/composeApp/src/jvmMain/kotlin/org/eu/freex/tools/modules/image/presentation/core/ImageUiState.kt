package org.eu.freex.tools.modules.image.presentation.core

import org.eu.freex.tools.modules.image.domain.model.ImageLayer
import org.eu.freex.tools.modules.image.domain.model.Pipeline
import org.eu.freex.tools.modules.image.domain.model.SegmentationProject


data class SegmentationInteraction(
    val selectedIndex: Int = -1,    // 游标
    val isLabeling: Boolean = false // 是否正在输入
)

enum class WorkbenchTab {
    FILTER,       // 原有的滤镜处理
    SEGMENTATION  // 新增的切割识别
}
data class ImageUiState(
    val isLoading: Boolean = false,
    val assets: List<ImageLayer> = emptyList(),
    val activeChain: Pipeline? = null,
    // [新增] 方便 UI 读取的快捷字段 (实际数据源在 Workspace)
    val segmentationProject: SegmentationProject? = null,
    // 临时状态
    val cropperLayer: ImageLayer? = null,
    val previewLayer: ImageLayer? = null,
    val isColorPicking: Boolean = false,
    // [新增] 界面状态
    val activeTab: WorkbenchTab = WorkbenchTab.FILTER,
    val segmentationInteraction: SegmentationInteraction = SegmentationInteraction()
) {
    val displayImage: ImageLayer?
        get() {
            if (cropperLayer != null) return cropperLayer
            if (previewLayer != null) return previewLayer
            if (activeChain != null) return activeChain.getActiveLayer(assets)
            return assets.firstOrNull()
        }
}