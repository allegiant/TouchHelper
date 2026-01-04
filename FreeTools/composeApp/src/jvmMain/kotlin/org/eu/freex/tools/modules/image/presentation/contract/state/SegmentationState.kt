package org.eu.freex.tools.modules.image.presentation.contract.state

import androidx.compose.ui.geometry.Rect
import org.eu.freex.tools.modules.image.domain.model.GridParams
import org.eu.freex.tools.modules.image.domain.model.WorkImage


// --- 分割/OCR 状态 ---
data class SegmentationState(
    val isGridMode: Boolean = true,
    val gridParams: GridParams = GridParams(0, 0, 100, 100, 0, 0, 1, 1),
    val activeRects: List<Rect> = emptyList(),
    val segmentationResults: List<WorkImage> = emptyList()
)