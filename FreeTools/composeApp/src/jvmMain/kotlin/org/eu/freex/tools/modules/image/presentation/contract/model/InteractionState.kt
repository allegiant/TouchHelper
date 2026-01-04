package org.eu.freex.tools.modules.image.presentation.contract.model

import java.awt.image.BufferedImage

// --- UI 交互状态 ---
data class UiInteractionState(
    val isLoading: Boolean = false,
    val rightPanelTabIndex: Int = 0,
    val isScreenCropperVisible: Boolean = false,
    val fullScreenCapture: BufferedImage? = null,
    val isMappingDialogVisible: Boolean = false,
    val mappingBitmap: BufferedImage? = null
)