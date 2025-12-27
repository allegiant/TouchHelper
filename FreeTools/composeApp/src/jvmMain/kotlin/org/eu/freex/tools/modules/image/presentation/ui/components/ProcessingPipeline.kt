package org.eu.freex.tools.modules.image.presentation.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.eu.freex.tools.model.WorkImage

/**
 * 3. 底部流水线 (ProcessingPipeline)
 * 显示处理步骤链条，横向滚动列表。
 */
@Composable
fun ProcessingPipeline(
    modifier: Modifier = Modifier,
    processChain: List<WorkImage>, // 包含原图+所有步骤
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDelete: (Int) -> Unit
) {
    Column(
        modifier = modifier.background(Color(0xFF3C3C3C))
    ) {
        // 标题栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .background(Color(0xFF252526))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "PIPELINE (处理流水线)",
                color = Color.LightGray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val listState = rememberLazyListState()

            LazyRow(
                state = listState,
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(processChain) { index, item ->
                    PipelineStepItem(
                        item = item,
                        index = index,
                        isSelected = (index == selectedIndex),
                        // 第一步(原图)通常不允许删除
                        isDeletable = index > 0,
                        onClick = { onSelect(index) },
                        onDelete = { onDelete(index) }
                    )
                }
            }

            // 滚动条
            HorizontalScrollbar(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                adapter = rememberScrollbarAdapter(listState)
            )
        }
    }
}

@Composable
private fun PipelineStepItem(
    item: WorkImage,
    index: Int,
    isSelected: Boolean,
    isDeletable: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(100.dp)
            .fillMaxHeight()
            .background(Color(0xFF2B2B2B))
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) Color(0xFF007ACC) else Color.Transparent
            )
            .clickable(onClick = onClick)
    ) {
        // 图片区域
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Image(
                bitmap = item.bitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().background(Color.Black)
            )

            // 步骤序号
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .background(Color.Black.copy(0.6f))
                    .padding(2.dp)
            ) {
                Text("${index}", color = Color.White, fontSize = 10.sp)
            }

            // 删除按钮 (右上角)
            if (isDeletable) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp)
                        .background(Color.Red.copy(0.7f))
                        .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Delete",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // 标签区域
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .background(if (isSelected) Color(0xFF007ACC) else Color(0xFF454545)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                item.label,
                color = Color.White,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
    }
}