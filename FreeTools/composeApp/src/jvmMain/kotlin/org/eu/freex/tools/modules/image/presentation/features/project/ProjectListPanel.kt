package org.eu.freex.tools.modules.image.presentation.features.project

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PickerMode
import io.github.vinceglb.filekit.core.PickerType
import org.eu.freex.tools.modules.image.domain.model.ImageLayer
import org.eu.freex.tools.modules.image.domain.model.LayerConfig
import org.eu.freex.tools.modules.image.presentation.core.ImageUiEvent
import org.eu.freex.tools.modules.image.presentation.core.LoadFile
import org.eu.freex.tools.modules.image.presentation.core.RemoveAsset
import org.eu.freex.tools.modules.image.presentation.core.SelectAsset
import java.io.File

@Composable
fun ProjectListPanel(
    modifier: Modifier = Modifier,
    assets: List<ImageLayer>,
    activeAssetId: String?,
    onEvent: (ImageUiEvent) -> Unit
) {
    val launcher = rememberFilePickerLauncher(
        type = PickerType.Image, // 限制只选择图片
        mode = PickerMode.Single, // 单选模式
        title = "导入图片素材" // 窗口标题
    ) { platformFile ->
        // 【新增 2】回调处理
        // 这里的 platformFile 是 FileKit 封装的对象
        // 在 JVM 平台上，platformFile.file 就是 java.io.File
        val file = platformFile?.file
        if (file != null) {
            onEvent(LoadFile(file))
        }
    }
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp)
    ) {
        // 1. 顶部标题栏 + 导入按钮
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "工程素材",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // 导入按钮
            IconButton(
                onClick = {
                    // 【新增 3】点击按钮时，启动选择器
                    launcher.launch()
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "导入图片",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(8.dp))

        // 2. 列表区域
        if (assets.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "暂无图片",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        "点击右上角 + 导入",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(assets, key = { it.id }) { layer ->
                    ProjectItem(
                        layer = layer,
                        isSelected = layer.id == activeAssetId,
                        onSelect = { onEvent(SelectAsset(layer.id)) },
                        onRemove = { onEvent(RemoveAsset(layer.id)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectItem(
    layer: ImageLayer,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 缩略图
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                // 判断逻辑：优先使用文件路径加载（Coil），否则使用内存对象（原生 Image）
                val config = layer.config
                // 尝试获取源文件路径
                val sourcePath = (config as? LayerConfig.Origin)?.sourcePath
                val file = if (sourcePath != null && sourcePath != "mem") File(sourcePath) else null

                if (file != null && file.exists()) {
                    // 【分支 A】本地文件：使用 Coil
                    // Coil 会在后台线程读取文件并缩放到 48dp，极大节省内存
                    AsyncImage(
                        model = ImageRequest.Builder(LocalPlatformContext.current)
                            .data(file)
                            .crossfade(true) // 淡入效果
                            .build(),
                        contentDescription = "File Thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (layer.image != null) {
                    // 【分支 B】内存图片（截图/滤镜结果）：使用原生 Image
                    // 此时 image 是 BufferedImage，直接转 ComposeBitmap
                    Image(
                        bitmap = layer.image.toComposeImageBitmap(),
                        contentDescription = "Memory Thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // 【分支 C】空状态
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // 名称
            Text(
                text = layer.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // 删除按钮 (只在选中或鼠标悬停时高亮，这里为了简单直接显示)
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}