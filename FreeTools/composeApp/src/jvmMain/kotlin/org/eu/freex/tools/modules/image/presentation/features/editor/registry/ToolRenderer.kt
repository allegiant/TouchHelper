package org.eu.freex.tools.modules.image.presentation.features.editor.registry

import androidx.compose.runtime.Composable
import org.eu.freex.tools.common.model.PickEvent
import java.awt.image.BufferedImage

/**
 * 工具渲染器标准接口
 * 所有工具必须实现此接口才能被注册
 */
fun interface ToolRenderer {
    @Composable
    fun Content(image: BufferedImage, onEvent: (PickEvent) -> Unit)
}