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
import org.eu.freex.tools.modules.image.domain.model.AppFilter
import org.eu.freex.tools.modules.image.domain.model.BinarizationFilter
import org.eu.freex.tools.modules.image.domain.model.BlackWhiteInvertFilter
import org.eu.freex.tools.modules.image.domain.model.ColorInvertFilter
import org.eu.freex.tools.modules.image.domain.model.DenoiseFilter
import org.eu.freex.tools.modules.image.domain.model.GrayscaleFilter
import org.eu.freex.tools.modules.image.domain.model.ViewFilter


@Composable
fun FilterSelectionList(
    modifier: Modifier = Modifier,
    currentFilter: AppFilter,
    onFilterChange: (AppFilter) -> Unit
) {
    // 准备数据
    val colorFilters = remember {
        listOf(
            ViewFilter,
            GrayscaleFilter,
            ColorInvertFilter
        )
    }
    val bwFilters = remember {
        listOf(
            BinarizationFilter(),
            BlackWhiteInvertFilter
        )
    }
    val commonFilters = remember {
        listOf(
            DenoiseFilter()
        )
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        FilterGroup(
            title = "针对彩色进行处理:",
            filters = colorFilters,
            currentFilter = currentFilter,
            onSelect = onFilterChange
        )

        FilterGroup(
            title = "针对黑白进行处理:",
            filters = bwFilters,
            currentFilter = currentFilter,
            onSelect = onFilterChange
        )

        FilterGroup(
            title = "通用预处理:",
            filters = commonFilters,
            currentFilter = currentFilter,
            onSelect = onFilterChange
        )
    }
}

@Composable
private fun FilterGroup(
    title: String,
    filters: List<AppFilter>,
    currentFilter: AppFilter,
    onSelect: (AppFilter) -> Unit
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
                            val isSelected = currentFilter::class == filter::class
                            FilterChip(
                                text = filter.name,
                                isSelected = isSelected,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (!isSelected) {
                                        onSelect(filter)
                                    }
                                }
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