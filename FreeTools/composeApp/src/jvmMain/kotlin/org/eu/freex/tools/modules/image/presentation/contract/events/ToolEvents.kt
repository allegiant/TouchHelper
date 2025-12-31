package org.eu.freex.tools.modules.image.presentation.contract.events

import androidx.compose.ui.geometry.Rect
import org.eu.freex.tools.common.utils.ImageUtils
import org.eu.freex.tools.modules.image.domain.model.AppSegmentation
import org.eu.freex.tools.modules.image.domain.model.AutoSegmentation
import org.eu.freex.tools.modules.image.domain.model.GridParams
import org.eu.freex.tools.modules.image.domain.model.GridSegmentation
import org.eu.freex.tools.modules.image.presentation.contract.ImageActionScope
import org.eu.freex.tools.modules.image.presentation.contract.ImageUiEvent

// --- 切割 ---
object PerformSegmentation : ImageUiEvent {
    override fun ImageActionScope.execute() {
        val source = state.project.activeDisplayImage ?: return

        val strategy: AppSegmentation = if (state.segmentation.isGridMode) {
            GridSegmentation(state.segmentation.gridParams)
        } else {
            AutoSegmentation(emptyList())
        }

        launch {
            segmentationProcessor.segment(source, strategy)
                .onSuccess { result ->
                    setSegmentation {
                        copy(
                            activeRects = result.rects,
                            segmentationResults = result.subImages,
                        )
                    }
                    showToast("识别到 ${result.rects.size} 个区域")
                }
        }
    }
}

data class UpdateGridParams(val params: GridParams) : ImageUiEvent {
    override fun ImageActionScope.execute() = setSegmentation { copy(gridParams = params) }
}

data class ToggleGridMode(val isGrid: Boolean) : ImageUiEvent {
    override fun ImageActionScope.execute() = setSegmentation { copy(isGridMode = isGrid) }
}

// --- 界面控制与弹窗 --

data class ChangePanelTab(val index: Int) : ImageUiEvent {
    override fun ImageActionScope.execute() {
        setUi { copy(rightPanelTabIndex = state.ui.rightPanelTabIndex) }
    }
}

object DismissDialogs : ImageUiEvent {
    override fun ImageActionScope.execute() {
        setUi { copy(isScreenCropperVisible = false, isMappingDialogVisible = false) }
    }
}

data class OpenMappingDialog(val rect: Rect) : ImageUiEvent {
    override fun ImageActionScope.execute() {
        val s = state.project.activeDisplayImage?.bufferedImage ?: return
        setUi { copy(isMappingDialogVisible = true, mappingBitmap = ImageUtils.cropImage(s, rect)) }
    }
}

data class ConfirmMapping(val char: String) : ImageUiEvent {
    override fun ImageActionScope.execute() {
        setUi { copy(isMappingDialogVisible = false, mappingBitmap = null) }
        showToast("映射已保存: $char")
    }
}