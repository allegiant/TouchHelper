package org.eu.freex.tools.modules.image.presentation.contract.events

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import org.eu.freex.tools.modules.image.domain.model.AppSegmentation
import org.eu.freex.tools.modules.image.domain.model.AutoSegmentation
import org.eu.freex.tools.modules.image.domain.model.GridParams
import org.eu.freex.tools.modules.image.domain.model.GridSegmentation
import org.eu.freex.tools.modules.image.presentation.contract.ImageActionScope
import org.eu.freex.tools.modules.image.presentation.contract.ImageUiEvent
import org.eu.freex.tools.common.utils.ImageUtils

// --- 切割 ---
object PerformSegmentation : ImageUiEvent {
    override fun execute(action: ImageActionScope) {
        val state = action.state
        val source = state.activeDisplayImage ?: return

        val strategy: AppSegmentation = if (state.isGridMode) {
            GridSegmentation(state.gridParams)
        } else {
            AutoSegmentation(emptyList())
        }

        action.launch {
            action.updateState { it.copy(isLoading = true) }
            action.segmentationProcessor.segment(source, strategy)
                .onSuccess { result ->
                    action.updateState {
                        it.copy(
                            activeRects = result.rects,
                            segmentationResults = result.subImages,
                            isLoading = false
                        )
                    }
                    action.showToast("识别到 ${result.rects.size} 个区域")
                }
        }
    }
}

data class UpdateGridParams(val params: GridParams) : ImageUiEvent {
    override fun execute(action: ImageActionScope) =
        action.updateState { it.copy(gridParams = params) }
}

data class ToggleGridMode(val isGrid: Boolean) : ImageUiEvent {
    override fun execute(action: ImageActionScope) =
        action.updateState { it.copy(isGridMode = isGrid) }
}

// --- 界面控制与弹窗 ---
data class UpdateCanvasTransform(val scale: Float, val offset: Offset) : ImageUiEvent {
    override fun execute(action: ImageActionScope) =
        action.updateState { it.copy(mainScale = scale, mainOffset = offset) }
}

data class ChangePanelTab(val index: Int) : ImageUiEvent {
    override fun execute(action: ImageActionScope) =
        action.updateState { it.copy(rightPanelTabIndex = index) }
}

object DismissDialogs : ImageUiEvent {
    override fun execute(action: ImageActionScope) = action.updateState {
        it.copy(isScreenCropperVisible = false, isMappingDialogVisible = false)
    }
}

data class OpenMappingDialog(val rect: Rect) : ImageUiEvent {
    override fun execute(action: ImageActionScope) {
        val s = action.state.activeDisplayImage?.bufferedImage ?: return
        action.updateState {
            it.copy(isMappingDialogVisible = true, mappingBitmap = ImageUtils.cropImage(s, rect))
        }
    }
}

data class ConfirmMapping(val char: String) : ImageUiEvent {
    override fun execute(action: ImageActionScope) {
        action.updateState { it.copy(isMappingDialogVisible = false, mappingBitmap = null) }
        action.showToast("映射已保存: $char")
    }
}

// 占位
data class HoverCanvas(val offset: Offset, val color: Color) : ImageUiEvent {
    override fun execute(action: ImageActionScope) {
        println("HoverCanvas: $offset, $color")
    }

}

data class ColorPick(val hex: String) : ImageUiEvent {
    override fun execute(action: ImageActionScope) {
        println("ColorPick: $hex")
    }
}