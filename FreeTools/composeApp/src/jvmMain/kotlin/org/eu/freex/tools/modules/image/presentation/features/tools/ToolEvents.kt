package org.eu.freex.tools.modules.image.presentation.features.tools

import org.eu.freex.tools.modules.image.presentation.core.ImageActionScope
import org.eu.freex.tools.modules.image.presentation.core.ImageUiEvent

// =================================================================================
// 2. 界面交互 (UI Interaction)
// =================================================================================

/**
 * 关闭所有弹窗
 */
object DismissDialogs : ImageUiEvent {
    override fun ImageActionScope.execute() {
        setScreenCropper(null)
    }
}