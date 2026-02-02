package org.eu.freex.tools.modules.image.presentation.components.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.modules.image.domain.model.ImageLayer

@Composable
fun WorkbenchTabRow(
    openedImages: List<ImageLayer>, // 数据源
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    onTabClosed: (ImageLayer) -> Unit
) {
    if (openedImages.isEmpty()) return

    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        modifier = Modifier.fillMaxWidth().height(40.dp), // 比较矮的标签栏
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        edgePadding = 0.dp
    ) {
        openedImages.forEachIndexed { index, image ->
            Tab(
                selected = index == selectedIndex,
                onClick = { onTabSelected(index) },
                text = { Text(image.name ?: "未命名") },
                // 选中时显示关闭按钮
                modifier = Modifier.background(
                    if (index == selectedIndex) MaterialTheme.colorScheme.surfaceContainerHigh
                    else Color.Transparent
                )
            )
        }
    }
}