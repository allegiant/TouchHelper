package org.eu.freex.tools.modules.image.presentation.features.filter.components


import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.modules.image.domain.model.BinarizationFilter
import org.eu.freex.tools.modules.image.domain.model.BlackWhiteInvertFilter
import org.eu.freex.tools.modules.image.domain.model.ColorInvertFilter
import org.eu.freex.tools.modules.image.domain.model.DenoiseFilter
import org.eu.freex.tools.modules.image.domain.model.GrayscaleFilter
import org.eu.freex.tools.modules.image.domain.model.ImageFilter
import org.eu.freex.tools.modules.image.domain.model.ViewFilter


@Composable
fun FilterSelectionList(
    modifier: Modifier = Modifier,
    currentFilter: ImageFilter,
    onFilterChange: (ImageFilter) -> Unit
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
    filters: List<ImageFilter>,
    currentFilter: ImageFilter,
    onSelect: (ImageFilter) -> Unit
) {
    Column {
        androidx.compose.material3.Text(
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

@Composable
fun FilterChip(
    text: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // 【修改】颜色映射
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(4.dp),
        border = if (isSelected) BorderStroke(1.dp, borderColor) else null,
        modifier = modifier
            .height(32.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(
                text = text,
                color = contentColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(
            Icons.Default.Tune,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary, // 【修改】主色
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            color = MaterialTheme.colorScheme.onSurface, // 【修改】
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
    }
}
