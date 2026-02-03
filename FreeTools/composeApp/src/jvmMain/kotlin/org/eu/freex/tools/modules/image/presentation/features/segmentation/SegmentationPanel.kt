package org.eu.freex.tools.modules.image.presentation.features.segmentation

// 引入组件

// Models

// ViewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eu.freex.tools.common.model.PickEvent
import org.eu.freex.tools.common.model.PickingToolState
import org.eu.freex.tools.modules.image.domain.model.SegmentationConfig
import org.eu.freex.tools.modules.image.presentation.features.segmentation.components.LabelingDialog
import org.eu.freex.tools.modules.image.presentation.features.segmentation.components.SegmentationConfigSection
import org.eu.freex.tools.modules.image.presentation.features.segmentation.components.SegmentationResultGrid
import org.eu.freex.tools.modules.image.presentation.viewmodel.EditorCanvasViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.FontLibraryViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.ImageWorkbenchViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.SegmentationViewModel
import org.koin.compose.koinInject
import java.awt.image.BufferedImage

@Composable
fun SegmentationPanel(
    modifier: Modifier = Modifier,
    segViewModel: SegmentationViewModel = koinInject(),
    workbenchViewModel: ImageWorkbenchViewModel = koinInject(),
    editorViewModel: EditorCanvasViewModel = koinInject(),
    fontViewModel: FontLibraryViewModel = koinInject()
) {
    val segState by segViewModel.uiState.collectAsState()
    val uiState = workbenchViewModel.uiState.collectAsState()

    val sourceImage = uiState.value.displayImage?.image

    val project = segState.project
    val config = project?.config ?: SegmentationConfig()
    val results = project?.results ?: emptyList()
    val labels = project?.labels ?: emptyMap()

    val bigComposeBitmap = remember(sourceImage) { sourceImage?.toComposeImageBitmap() }
    val slicedCache = remember { mutableStateListOf<ImageBitmap?>() }

    // [核心修复] 使用 rememberUpdatedState 保持对最新 config 的引用
    // 这样在 LaunchedEffect 内部就能总是获取到界面上最新的配置，而不是第一次加载时的旧配置
    val currentConfig by rememberUpdatedState(config)

    LaunchedEffect(Unit) {
        workbenchViewModel.pickEvent.collect { event ->
            if (event is PickEvent.PointPicked) {
                // 使用 currentConfig (最新状态) 进行拷贝
                val newConfig = currentConfig.copy(startX = event.x, startY = event.y)

                // 更新并运行算法
                segViewModel.runSegmentation(newConfig)

                // 取点完成后退出取点模式
                workbenchViewModel.activeTool(PickingToolState.None)
            }
        }
    }

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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
        ) {
            SegmentationConfigSection(
                config = config,
                onChange = { newConfig -> segViewModel.runSegmentation(newConfig) },
                onPickPoint = {
                    workbenchViewModel.activeTool(PickingToolState.PointPicker)
                }
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

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
                    selectedIndex = segState.selectedIndex,
                    sourceImage = bigComposeBitmap,
                    slicedImages = slicedCache,
                    onSelectChar = { index -> segViewModel.selectRect(index) },
                    onDoubleTap = { index ->
                        segViewModel.selectRect(index)
                        segViewModel.showLabelDialog()
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        if (sourceImage != null) {
                            val batchItems = results.mapIndexedNotNull { index, rect ->
                                val label = labels[index]
                                if (!label.isNullOrBlank()) {
                                    rect to label
                                } else null
                            }
                            if (batchItems.isNotEmpty()) {
                                fontViewModel.addToLibrary(batchItems)
                            }
                        }
                    },
                    enabled = labels.isNotEmpty()
                ) {
                    Text("保存已识别字符到字库")
                }
            }
        }

        if (segState.isLabeling && segState.selectedIndex in results.indices && bigComposeBitmap != null) {
            LabelingDialog(
                rect = results[segState.selectedIndex],
                sourceImage = bigComposeBitmap,
                initialText = labels[segState.selectedIndex] ?: "",
                onConfirm = { text -> segViewModel.submitLabel(text) },
                onDismiss = { segViewModel.dismissLabelDialog() }
            )
        }
    }
}