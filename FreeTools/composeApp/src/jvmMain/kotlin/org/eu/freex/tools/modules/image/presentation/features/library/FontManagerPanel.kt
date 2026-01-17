package org.eu.freex.tools.modules.image.presentation.features.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.eu.freex.tools.modules.image.domain.model.FontLibItem
import java.io.File
import javax.swing.JFileChooser

@Composable
fun FontManagerPanel(
    library: List<FontLibItem>,
    onDelete: (String) -> Unit,
    onSort: () -> Unit,
    onClear: () -> Unit,
    onExport: (File) -> Unit
) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // 过滤逻辑
    val filteredLibrary = remember(library, searchQuery) {
        if (searchQuery.isBlank()) library
        else library.filter { it.charName.contains(searchQuery) }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {

        // 1. 顶部工具栏
        Surface(tonalElevation = 2.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 搜索框
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("搜索字符") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.width(200.dp).height(56.dp),
                    singleLine = true
                )

                Spacer(Modifier.weight(1f))

                IconButton(onClick = {
                    val chooser = JFileChooser()
                    if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                        onExport(chooser.selectedFile)
                    }
                }) { Icon(Icons.Default.Save, "导出") }

                IconButton(onClick = onSort) { Icon(Icons.Default.SortByAlpha, "排序") }

                IconButton(onClick = onClear) { Icon(Icons.Default.DeleteForever, "清空") }
            }
        }

        // 2. 二级操作栏 (针对选中项)
        if (selectedId != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).height(40.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("已选中: ${filteredLibrary.find { it.id == selectedId }?.charName ?: ""}", style = MaterialTheme.typography.bodySmall)

                Spacer(Modifier.weight(1f))

                Button(
                    onClick = {
                        selectedId?.let { onDelete(it) }
                        selectedId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("删除", fontSize = 12.sp)
                }
            }
        }

        Divider()

        // 3. 网格显示
        if (filteredLibrary.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无字库数据", color = Color.Gray)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 60.dp),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredLibrary, key = { it.id }) { item ->
                    FontItemCell(
                        item = item,
                        isSelected = item.id == selectedId,
                        onClick = { selectedId = item.id }
                    )
                }
            }
        }

        // 4. 底部状态
        Surface(tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth().height(24.dp)) {
            Text(
                " 共 ${filteredLibrary.size} 个字符",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun FontItemCell(item: FontLibItem, isSelected: Boolean, onClick: () -> Unit) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val borderWidth = if (isSelected) 2.dp else 1.dp
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow

    Column(
        modifier = Modifier
            .aspectRatio(0.8f)
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 图片区域
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().background(Color.White), // 字库通常是黑底白字或白底黑字，这里给个白底方便看
            contentAlignment = Alignment.Center
        ) {
            if (item.displayBitmap != null) {
                // 显示图片
                androidx.compose.foundation.Image(
                    bitmap = item.displayBitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.None // 像素风格
                )
            } else {
                // 如果没有图片缓存（导入的数据），显示占位符或尝试绘制01串（可选优化）
                Text("No Img", fontSize = 8.sp, color = Color.Gray)
            }
        }

        Spacer(Modifier.height(4.dp))

        // 字符名称
        Text(
            text = item.charName,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        // 尺寸信息
        Text(
            text = "${item.width}x${item.height}",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 8.sp,
            color = Color.Gray
        )
    }
}