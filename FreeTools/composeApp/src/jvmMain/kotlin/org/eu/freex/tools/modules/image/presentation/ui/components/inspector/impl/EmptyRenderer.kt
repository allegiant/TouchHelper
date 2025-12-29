package org.eu.freex.tools.modules.image.presentation.ui.components.inspector.impl

import androidx.compose.runtime.Composable
import org.eu.freex.tools.modules.image.presentation.ui.components.inspector.core.FilterRenderer

object EmptyRenderer : FilterRenderer {
    @Composable
    override fun Content() {
        // 什么都不显示，或者显示“无参数”
        // Text("此滤镜无可配置项")
    }
}