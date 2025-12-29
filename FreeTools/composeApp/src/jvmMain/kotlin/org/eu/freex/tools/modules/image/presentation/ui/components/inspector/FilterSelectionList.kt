package org.eu.freex.tools.modules.image.presentation.ui.components.inspector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme // 【修改】引入 Material3
import androidx.compose.material3.Text         // 【修改】引入 Material3 Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.model.FilterConstantsUI
import org.eu.freex.tools.model.label
import uniffi.touch_core.BlackWhiteFilterType
import uniffi.touch_core.ColorFilterType
import uniffi.touch_core.CommonFilterType
import uniffi.touch_core.ImageFilter

@Composable
fun FilterSelectionList(
    modifier: Modifier = Modifier,
    currentFilter: ImageFilter,
    onFilterChange: (ImageFilter) -> Unit
) {
    // 准备数据
    val colorFilters = remember { ColorFilterType.values().map { ImageFilter.Color(it) } }
    val bwFilters = remember { BlackWhiteFilterType.values().map { ImageFilter.BlackWhite(it) } }
    val commonFilters = remember { CommonFilterType.values().map { ImageFilter.Common(it) } }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        FilterGroup(
            title = FilterConstantsUI.GROUP_COLOR,
            filters = colorFilters,
            currentFilter = currentFilter,
            onSelect = onFilterChange
        )

        FilterGroup(
            title = FilterConstantsUI.GROUP_BINARY,
            filters = bwFilters,
            currentFilter = currentFilter,
            onSelect = onFilterChange
        )

        FilterGroup(
            title = FilterConstantsUI.GROUP_COMMON,
            filters = commonFilters,
            currentFilter = currentFilter,
            onSelect = onFilterChange
        )
    }
}

@Composable
private fun FilterGroup(
    title: String,
    filters: List<ImageFilter>,
    currentFilter: ImageFilter,
    onSelect: (ImageFilter) -> Unit
) {
    Column {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        val rows = remember(filters) { filters.chunked(3) }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { rowFilters ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (i in 0 until 3) {
                        if (i < rowFilters.size) {
                            val filter = rowFilters[i]
                            // 注意：FilterChip 已经在 SharedComponents.kt 中适配过颜色了
                            FilterChip(
                                text = filter.label,
                                isSelected = isSameFilter(currentFilter, filter),
                                modifier = Modifier.weight(1f),
                                onClick = { onSelect(filter) }
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}