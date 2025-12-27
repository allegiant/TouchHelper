package org.eu.freex.tools.modules.image.presentation.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.eu.freex.tools.model.WorkImage
import org.eu.freex.tools.utils.ImageUtils
import java.io.File


/**
 * 1. 左侧资源管理器 (ProjectExplorer)
 * 这个组件负责显示图片列表，并提供导入和截图的入口。
 */
@Composable
fun ProjectExplorer(
    modifier: Modifier = Modifier,
    sourceImages: List<WorkImage>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onImportFile: (File) -> Unit,
    onScreenCapture: () -> Unit
) {
    Column(
        modifier = modifier
            .background(Color(0xFF252526)) // VSCode 风格深色背景
            .drawBehind {
                // 右侧分割线
                drawLine(
                    color = Color(0xFF3E3E42),
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
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            // 导入按钮
            IconButton(
                onClick = {
                    ImageUtils.pickFile()?.let { onImportFile(it) }
                },
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Import",
                    tint = Color.LightGray
                )
            }
        }

        // --- 截图按钮 ---
        Button(
            onClick = onScreenCapture,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .height(28.dp),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = Color(0xFF007ACC), // VSCode 蓝色
                contentColor = Color.White
            )
        ) {
            Text("截图导入", fontSize = 12.sp)
        }

        Spacer(Modifier.height(8.dp))

        // --- 资源列表 ---
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f)
        ) {
            itemsIndexed(sourceImages) { index, item ->
                val isSelected = (index == selectedIndex)
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(
                if (isSelected) Color(0xFF37373D) else Color.Transparent,
                RoundedCornerShape(4.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 图片缩略图
        Image(
            bitmap = item.bitmap,
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
            color = Color.LightGray,
            fontSize = 12.sp,
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
                tint = if (isSelected) Color.White else Color.Gray,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}