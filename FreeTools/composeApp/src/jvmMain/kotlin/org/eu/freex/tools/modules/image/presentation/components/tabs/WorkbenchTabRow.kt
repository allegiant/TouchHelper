package org.eu.freex.tools.modules.image.presentation.components.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.eu.freex.tools.modules.image.domain.model.ImageLayer

@Composable
fun WorkbenchTabRow(
    openedImages: List<ImageLayer>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    onTabClosed: (ImageLayer) -> Unit
) {
    if (openedImages.isEmpty()) return

    // 1. 安全索引 (防崩溃)
    val safeIndex = selectedIndex.coerceIn(0, (openedImages.size - 1).coerceAtLeast(0))

    // 2. 容器设计
    ScrollableTabRow(
        selectedTabIndex = safeIndex,
        modifier = Modifier.fillMaxWidth().height(36.dp), // [优化] 稍微变矮一点，更精致
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        edgePadding = 0.dp,
        divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) },
        // [优化] 隐藏原本粗重的下划线指示器，或者做一个极细的线条
        indicator = { tabPositions ->
            if (safeIndex < tabPositions.size) {
                Box(
                    Modifier
                        .tabIndicatorOffset(tabPositions[safeIndex])
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    ) {
        openedImages.forEachIndexed { index, image ->
            EditorTabItem(
                title = image.name ?: "无标题-${index + 1}",
                isSelected = index == safeIndex,
                onClick = { onTabSelected(index) },
                onClose = { onTabClosed(image) }
            )
        }
    }
}

@Composable
private fun EditorTabItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        Color.Transparent
    }

    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .height(36.dp), // 确保高度一致
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // 标题
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(if (title.length > 10) 100.dp else androidx.compose.ui.unit.Dp.Unspecified) // 限制过长文本
        )

        // 选中状态或者是鼠标悬停时才显示关闭按钮？这里简化为一直显示或选中显示
        // [优化] 增加关闭按钮
        Spacer(modifier = Modifier.width(8.dp))

        // 自定义微型关闭按钮
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null, // 去掉点击波纹，避免干扰 Tab 点击
                    onClick = onClose
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = contentColor.copy(alpha = 0.6f),
                modifier = Modifier.size(12.dp)
            )
        }
    }
}