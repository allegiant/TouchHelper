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

    // [修复]：计算安全索引
    // 即使 ViewModel 中的 selectedIndex 跑到了 1，而 openedImages 这里还是长度 1，
    // 我们强制把它限制在 0，防止 TabRow 内部崩溃。
    val safeIndex = selectedIndex.coerceIn(0, (openedImages.size - 1).coerceAtLeast(0))

    ScrollableTabRow(
        selectedTabIndex = safeIndex, // 使用安全索引
        modifier = Modifier.fillMaxWidth().height(40.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        edgePadding = 0.dp
    ) {
        openedImages.forEachIndexed { index, image ->
            Tab(
                selected = index == safeIndex, // 这里也建议用 safeIndex 判断高亮
                onClick = { onTabSelected(index) },
                text = { Text(image.name ?: "未命名") },
                modifier = Modifier.background(
                    if (index == safeIndex) MaterialTheme.colorScheme.surfaceContainerHigh
                    else Color.Transparent
                )
            )
        }
    }
}