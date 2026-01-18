package org.eu.freex.tools.modules.image.presentation.features.pipeline

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.eu.freex.tools.modules.image.presentation.viewmodel.PipelineViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.ProjectListViewModel
import org.koin.compose.koinInject
import java.awt.image.BufferedImage

@Composable
fun ProcessingPipeline(
    modifier: Modifier = Modifier,
    pipelineViewModel: PipelineViewModel = koinInject(),
    projectViewModel: ProjectListViewModel = koinInject()
) {
    val pipelineState by pipelineViewModel.uiState.collectAsState()
    val projectState by projectViewModel.uiState.collectAsState()

    val chain = pipelineState.pipeline

    // 容器背景色
    val containerColor = MaterialTheme.colorScheme.surfaceContainer

    // 【关键修复】如果 chain 为空，显示占位提示，而不是 return
    if (chain == null) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .background(containerColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "请在左侧选择一张图片开始编辑",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
        return
    }

    // --- 以下是正常的渲染逻辑 ---

    val inputAsset = remember(chain.inputAssetId, projectState.assets) {
        projectState.assets.find { it.id == chain.inputAssetId }
    }

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. 原图节点
        item {
            PipelineNode(
                name = "原图",
                image = inputAsset?.image,
                isSelected = chain.activeIndex == -1,
                onClick = { pipelineViewModel.selectStep(-1) }
            )
        }

        // 2. 步骤节点
        itemsIndexed(chain.steps) { index, layer ->
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )

            Spacer(Modifier.width(8.dp))

            PipelineNode(
                name = layer.name,
                image = layer.image,
                isSelected = chain.activeIndex == index,
                onClick = { pipelineViewModel.selectStep(index) },
                onRemove = { pipelineViewModel.removeFilter(index) }
            )
        }
    }
}

// ... PipelineNode 保持不变 ...
@Composable
private fun PipelineNode(
    name: String,
    image: BufferedImage?,
    isSelected: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null
) {
    // ... (保留您原有的 PipelineNode 代码) ...
    // 为节省篇幅，这里复用您刚才上传的 PipelineNode 实现
    // 选中时使用 Primary 色高亮，未选中透明
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val nameStyle = if (isSelected)
        MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.primary)
    else
        MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurface)

    val shape = RoundedCornerShape(6.dp)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(100.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .border(2.dp, borderColor, shape)
                .clip(shape)
                .background(Color.Black)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (image != null) {
                Image(
                    bitmap = image.toComposeImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text("No Data", color = Color.Gray, fontSize = 9.sp)
            }

            if (onRemove != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable(onClick = onRemove),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove Step",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Text(
            text = name,
            style = nameStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}