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
import org.eu.freex.tools.modules.image.domain.model.ImageLayer
import org.eu.freex.tools.modules.image.presentation.viewmodel.PipelineViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.ProjectListViewModel
import org.koin.compose.koinInject
import java.awt.image.BufferedImage

@Composable
fun ProcessingPipeline(
    modifier: Modifier = Modifier,
    // [新架构] 注入 ViewModel，不再依赖外部传参
    pipelineViewModel: PipelineViewModel = koinInject(),
    // [新架构] 为了获取 Input Asset (原图)，我们需要访问资源列表，可以通过 ProjectListViewModel 或直接 Repo
    // 这里简单起见注入 ProjectListViewModel
    projectViewModel: ProjectListViewModel = koinInject()
) {
    // 1. 监听 ViewModel 状态
    val pipelineState by pipelineViewModel.uiState.collectAsState()
    val projectState by projectViewModel.uiState.collectAsState()

    val chain = pipelineState.pipeline ?: return

    // 2. 计算原图 (Input Asset)
    // 监听资源列表，找到 ID 匹配的那张图
    val inputAsset = remember(chain.inputAssetId, projectState.assets) {
        projectState.assets.find { it.id == chain.inputAssetId }
    }

    // 容器背景色稍微亮一点，区分于画布
    val containerColor = MaterialTheme.colorScheme.surfaceContainer

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
                // [变更] 直接调用 VM 方法
                onClick = { pipelineViewModel.selectStep(-1) }
            )
        }

        // 2. 步骤节点 (带箭头)
        itemsIndexed(chain.steps) { index, layer ->
            // 简单的箭头连接符
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
                // [变更] 直接调用 VM 方法
                onClick = { pipelineViewModel.selectStep(index) },
                onRemove = { pipelineViewModel.removeFilter(index) }
            )
        }
    }
}

// --- 以下 UI 组件完全保留您的原有设计 ---

@Composable
private fun PipelineNode(
    name: String,
    image: BufferedImage?,
    isSelected: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null
) {
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

            // 删除按钮
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