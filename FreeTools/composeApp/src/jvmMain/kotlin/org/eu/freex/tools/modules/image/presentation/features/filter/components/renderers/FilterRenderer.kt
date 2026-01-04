package org.eu.freex.tools.modules.image.presentation.features.filter.components.renderers

import androidx.compose.runtime.Composable
import org.eu.freex.tools.modules.image.domain.model.ImageFilter

interface FilterRenderer {
    @Composable
    fun Content(
        filter: ImageFilter,              // 输入：当前的数据
        onFilterChange: (ImageFilter) -> Unit // 输出：变更回调
    )
}