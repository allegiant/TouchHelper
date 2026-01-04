package org.eu.freex.tools.modules.image.presentation.features.filter.components.renderers

import androidx.compose.runtime.Composable
import org.eu.freex.tools.modules.image.domain.model.ImageFilter

object EmptyRenderer : FilterRenderer {
    @Composable
    override fun Content(filter: ImageFilter, onFilterChange: (ImageFilter) -> Unit) {
        // 什么都不显示，或者显示“无参数”
        // Text("此滤镜无可配置项")
    }
}