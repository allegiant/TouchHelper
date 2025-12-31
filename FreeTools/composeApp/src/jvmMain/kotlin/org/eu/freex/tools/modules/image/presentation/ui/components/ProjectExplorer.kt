package org.eu.freex.tools.modules.image.presentation.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.modules.image.domain.model.WorkImage
import org.eu.freex.tools.common.utils.ImageUtils
import org.eu.freex.tools.modules.image.presentation.contract.ProjectState
import java.io.File


/**
 * 1. 左侧资源管理器 (ProjectExplorer)
 * 这个组件负责显示图片列表，并提供导入和截图的入口。
 */
@Composable
fun ProjectExplorer(
    modifier: Modifier = Modifier,
    projectState: ProjectState,
    onSelect: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onImportFile: (File) -> Unit,
    onScreenCapture: () -> Unit
) {
    // 获取当前主题的颜色
    val surfaceColor = MaterialTheme.colorScheme.surface
    val borderColor = MaterialTheme.colorScheme.outlineVariant // M3 推荐的边框色
    Column(
        modifier = modifier
            .background(surfaceColor)
            .drawBehind {
                // 右侧分割线
                drawLine(
                    color = borderColor,
                    start = Offset(size.width, 0f),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
    ) {
        // --- 顶部标题栏 ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "工程资源",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            // 导入按钮
            IconButton(
                onClick = {
                    ImageUtils.pickFile()?.let { onImportFile(it) }
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Import",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant // 【修改】次级图标色
                )
            }
        }

        // --- 截图按钮 ---
        Button(
            onClick = onScreenCapture,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .height(36.dp),
            contentPadding = PaddingValues(0.dp),
            shape = RoundedCornerShape(4.dp), // 保持您喜欢的方角风格
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text("截图导入", style = MaterialTheme.typography.labelMedium)
        }

        Spacer(Modifier.height(8.dp))

        // --- 资源列表 ---
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f)
        ) {
            itemsIndexed(projectState.sourceImages) { index, item ->
                val isSelected = (index == projectState.selectedSourceIndex)
                ResourceItem(
                    item = item,
                    isSelected = isSelected,
                    onClick = { onSelect(index) },
                    onDelete = { onRemove(index) }
                )
            }
        }
    }
}

@Composable
private fun ResourceItem(
    item: WorkImage,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val imageBitmap = remember(item.bufferedImage) {
        item.bufferedImage.toComposeImageBitmap()
    }
    // 【修改】选中态使用 SecondaryContainer 或 SurfaceVariant
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        Color.Transparent
    }

    // 【修改】选中态文字颜色
    val textColor = if (isSelected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(backgroundColor, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 图片缩略图
        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            modifier = Modifier
                .size(28.dp)
                .background(Color.Black),
            contentScale = ContentScale.Fit
        )

        Spacer(Modifier.width(8.dp))

        // 文件名
        Text(
            text = item.name,
            color = textColor,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // 删除按钮 (仅选中时显示，或者一直显示，这里设定一直显示但颜色淡)
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = textColor.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}