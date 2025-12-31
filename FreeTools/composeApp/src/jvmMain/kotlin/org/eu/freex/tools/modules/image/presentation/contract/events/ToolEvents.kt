package org.eu.freex.tools.modules.image.presentation.contract.events

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
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
        val source = state.activeDisplayImage ?: return

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

// --- 界面控制与弹窗 ---
data class UpdateCanvasTransform(val scale: Float, val offset: Offset) : ImageUiEvent {
    override fun ImageActionScope.execute() {
        setCanvas { copy(mainScale = scale, mainOffset = offset) }
    }
}

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
        val s = state.activeDisplayImage?.bufferedImage ?: return
        setUi { copy(isMappingDialogVisible = true, mappingBitmap = ImageUtils.cropImage(s, rect)) }
    }
}

data class ConfirmMapping(val char: String) : ImageUiEvent {
    override fun ImageActionScope.execute() {
        setUi { copy(isMappingDialogVisible = false, mappingBitmap = null) }
        showToast("映射已保存: $char")
    }
}

// 占位
data class HoverCanvas(val offset: Offset, val color: Color) : ImageUiEvent {
    override fun ImageActionScope.execute() {
        println("HoverCanvas: $offset, $color")
    }

}

data class ColorPick(val hex: String) : ImageUiEvent {
    override fun ImageActionScope.execute() {
        println("ColorPick: $hex")
    }
}