package org.eu.freex.tools.modules.image.presentation.features.pipeline

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.modules.image.domain.model.WorkImage
import org.eu.freex.tools.modules.image.presentation.features.project.ProjectState

/**
 * 3. 底部流水线 (ProcessingPipeline)
 * 显示处理步骤链条，横向滚动列表。
 */
@Composable
fun ProcessingPipeline(
    modifier: Modifier = Modifier,
    processChain: List<WorkImage>, // 包含原图+所有步骤
    projectState: ProjectState,
    onSelect: (Int) -> Unit,
    onDelete: (Int) -> Unit
) {
    // 【修改】容器背景色
    val containerColor = MaterialTheme.colorScheme.surfaceContainer
    val titleBarColor = MaterialTheme.colorScheme.surface // 稍微深/浅一点以区分标题
    Column(
        modifier = modifier.background(containerColor)
    ) {
        // 标题栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(titleBarColor)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "PIPELINE (处理流水线)",
                color = MaterialTheme.colorScheme.onSurfaceVariant, // 【修改】
                style = MaterialTheme.typography.labelSmall,
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
                        isSelected = (index == projectState.selectedSourceIndex),
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
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant // 卡片背景

    Column(
        modifier = Modifier
            .width(100.dp)
            .fillMaxHeight()
            .background(backgroundColor)
            .border(width = if (isSelected) 2.dp else 0.dp, color = borderColor)
            .clickable(onClick = onClick)
    ) {
        // 【优化】缩略图懒加载
        val thumbBitmap = remember(item) {
            item.bufferedImage.toComposeImageBitmap()
        }
        // 图片区域
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Image(
                bitmap = thumbBitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().background(Color.Black) // 图片预览背景保持黑
            )

            // 步骤序号
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                    .padding(2.dp)
            ) {
                Text(
                    "$index",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            // 删除按钮
            if (isDeletable) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.8f)) // 【修改】语义错误色
                        .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // 标签区域
        val labelBg = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
        val labelColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .background(labelBg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                item.label,
                color = labelColor,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}