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
import org.eu.freex.tools.modules.image.domain.model.Pipeline
import org.eu.freex.tools.modules.image.presentation.core.ImageUiEvent
import org.eu.freex.tools.modules.image.presentation.core.RemoveStep
import org.eu.freex.tools.modules.image.presentation.core.SelectStep
import java.awt.image.BufferedImage

@Composable
fun ProcessingPipeline(
    modifier: Modifier = Modifier,
    chain: Pipeline?,
    assets: List<ImageLayer>,
    onEvent: (ImageUiEvent) -> Unit
) {
    if (chain == null) return

    val inputAsset = remember(chain.inputAssetId, assets) {
        assets.find { it.id == chain.inputAssetId }
    }

    // 容器背景色稍微亮一点，区分于画布
    val containerColor = MaterialTheme.colorScheme.surfaceContainer

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), // 减小间距
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. 原图节点
        item {
            PipelineNode(
                name = "原图",
                image = inputAsset?.image,
                isSelected = chain.activeIndex == -1,
                onClick = { onEvent(SelectStep(-1)) }
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
                name = layer.name, // 去掉序号，更简洁
                image = layer.image,
                isSelected = chain.activeIndex == index,
                onClick = { onEvent(SelectStep(index)) },
                onRemove = { onEvent(RemoveStep(index)) }
            )
        }
    }
}

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
        modifier = Modifier.width(100.dp) // 【优化】宽度从 120 -> 100
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp) // 【优化】高度从 90 -> 72
                .border(2.dp, borderColor, shape)
                .clip(shape)
                .background(Color.Black) // 图片未加载时黑底
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (image != null) {
                Image(
                    bitmap = image.toComposeImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit, // 完整显示
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text("No Data", color = Color.Gray, fontSize = 9.sp)
            }
            // [新增] 删除按钮 (如果有回调则显示)
            if (onRemove != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd) // 右上角
                        .padding(4.dp)
                        .size(20.dp) // 触摸热区
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f)) // 半透明黑底
                        .clickable(onClick = onRemove), // 点击触发删除，因为在 Box 上层，会拦截 onClick
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