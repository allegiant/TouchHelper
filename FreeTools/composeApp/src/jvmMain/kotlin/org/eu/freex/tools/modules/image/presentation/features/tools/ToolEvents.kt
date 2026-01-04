package org.eu.freex.tools.modules.image.presentation.features.tools

import androidx.compose.ui.geometry.Rect
import org.eu.freex.tools.common.utils.ImageUtils
import org.eu.freex.tools.modules.image.domain.model.Segmentation
import org.eu.freex.tools.modules.image.domain.model.AutoSegmentation
import org.eu.freex.tools.modules.image.domain.model.GridParams
import org.eu.freex.tools.modules.image.domain.model.GridSegmentation
import org.eu.freex.tools.modules.image.presentation.core.ImageActionScope
import org.eu.freex.tools.modules.image.presentation.core.ImageUiEvent

// =================================================================================
// 2. 界面交互 (UI Interaction)
// =================================================================================

/**
 * 切换右侧面板 Tab
 */
data class ChangePanelTab(val index: Int) : ImageUiEvent {
    override fun ImageActionScope.execute() {
        // 【修复】原代码直接赋值了旧状态，现已修正为使用传入的 index
        setUi { copy(rightPanelTabIndex = index) }
    }
}

/**
 * 关闭所有弹窗
 */
object DismissDialogs : ImageUiEvent {
    override fun ImageActionScope.execute() {
        setUi {
            copy(
                isScreenCropperVisible = false,
                isMappingDialogVisible = false,
                fullScreenCapture = null,
                mappingBitmap = null
            )
        }
    }
}

/**
 * 确认映射
 * 逻辑：(示例) 打印日志或保存映射关系
 */
data class ConfirmMapping(val char: String) : ImageUiEvent {
    override fun ImageActionScope.execute() {
        showToast("已建立映射: $char")
        // 关闭弹窗
        setUi { copy(isMappingDialogVisible = false, mappingBitmap = null) }

        // TODO: 实际业务中，这里应该调用 repository 保存 OCR 训练数据或映射表
    }
}