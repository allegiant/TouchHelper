package org.eu.freex.tools.modules.image.presentation.features.segmentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage

// 引入拆分后的组件
import org.eu.freex.tools.modules.image.presentation.features.segmentation.components.SegmentationConfigSection
import org.eu.freex.tools.modules.image.presentation.features.segmentation.components.SegmentationResultGrid
import org.eu.freex.tools.modules.image.presentation.features.segmentation.components.LabelingDialog

import org.eu.freex.tools.modules.image.domain.model.SegmentationConfig
import org.eu.freex.tools.modules.image.domain.model.SegmentationRect
import org.eu.freex.tools.modules.image.presentation.core.LocalImageViewModel
import org.eu.freex.tools.modules.image.presentation.core.SegmentationInteraction

@Composable
fun SegmentationPanel(
    modifier: Modifier = Modifier,
    config: SegmentationConfig,
    results: List<SegmentationRect>,
    labels: Map<Int, String>,
    interaction: SegmentationInteraction,
    sourceImage: BufferedImage?,
    onConfigChange: (SegmentationConfig) -> Unit,
    onSelectChar: (Int) -> Unit,
    onSubmitLabel: (String) -> Unit,
    onStopLabeling: () -> Unit
) {
    val viewModel = LocalImageViewModel.current
    val scope = rememberCoroutineScope()

    val bigComposeBitmap = remember(sourceImage) { sourceImage?.toComposeImageBitmap() }
    val slicedCache = remember { mutableStateListOf<ImageBitmap?>() }

    // 异步切片逻辑
    LaunchedEffect(results, sourceImage) {
        if (sourceImage == null || results.isEmpty()) {
            slicedCache.clear()
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            val newSlices = ArrayList<ImageBitmap?>(results.size)
            for (rect in results) {
                try {
                    val rW = rect.width.toInt()
                    val rH = rect.height.toInt()
                    if (rW > 0 && rH > 0 &&
                        rect.left >= 0 && rect.top >= 0 &&
                        (rect.left + rW) <= sourceImage.width &&
                        (rect.top + rH) <= sourceImage.height
                    ) {
                        val subView = sourceImage.getSubimage(rect.left, rect.top, rW, rH)
                        val copy = BufferedImage(rW, rH, BufferedImage.TYPE_INT_ARGB)
                        val g = copy.createGraphics()
                        g.drawImage(subView, 0, 0, null)
                        g.dispose()
                        newSlices.add(copy.toComposeImageBitmap())
                    } else {
                        newSlices.add(null)
                    }
                } catch (e: Exception) {
                    newSlices.add(null)
                }
            }
            withContext(Dispatchers.Main) {
                slicedCache.clear()
                slicedCache.addAll(newSlices)
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(8.dp)) {
        // 1. 配置区域 (滚动)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false) // 自适应高度，不强占
                .verticalScroll(rememberScrollState())
        ) {
            SegmentationConfigSection(
                config = config,
                onChange = onConfigChange,
                onPickPoint = {
                    scope.launch {
                        val point = viewModel.awaitPointPick()
                        if (point != null) {
                            onConfigChange(config.copy(startX = point.x, startY = point.y))
                        }
                    }
                }
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // 2. 结果网格 (剩余空间)
        if (results.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "暂无切割结果，请调整上方参数",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        } else {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                SegmentationResultGrid(
                    results = results,
                    labels = labels,
                    selectedIndex = interaction.selectedIndex,
                    sourceImage = bigComposeBitmap,
                    slicedImages = slicedCache,
                    onSelectChar = onSelectChar
                )
            }

            Text(
                text = "共切割出 ${results.size} 个字符",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 4.dp).align(Alignment.End)
            )
        }

        // 3. 弹窗
        if (interaction.isLabeling && interaction.selectedIndex in results.indices && bigComposeBitmap != null) {
            LabelingDialog(
                rect = results[interaction.selectedIndex],
                sourceImage = bigComposeBitmap,
                initialText = labels[interaction.selectedIndex] ?: "",
                onConfirm = onSubmitLabel,
                onDismiss = onStopLabeling
            )
        }
    }
}