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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eu.freex.tools.common.model.PickingType
import org.koin.compose.koinInject
import java.awt.image.BufferedImage
import java.util.ArrayList

// 引入拆分后的组件
import org.eu.freex.tools.modules.image.presentation.features.segmentation.components.SegmentationConfigSection
import org.eu.freex.tools.modules.image.presentation.features.segmentation.components.SegmentationResultGrid
import org.eu.freex.tools.modules.image.presentation.features.segmentation.components.LabelingDialog

// Domain Models
import org.eu.freex.tools.modules.image.domain.model.SegmentationConfig

// ViewModels
import org.eu.freex.tools.modules.image.presentation.viewmodel.SegmentationViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.EditorCanvasViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.FontLibraryViewModel

@Composable
fun SegmentationPanel(
    modifier: Modifier = Modifier,
    // [新架构] 注入所需的 ViewModel
    segViewModel: SegmentationViewModel = koinInject(),
    editorViewModel: EditorCanvasViewModel = koinInject(),
    fontViewModel: FontLibraryViewModel = koinInject()
) {
    // 1. 监听状态
    val segState by segViewModel.uiState.collectAsState()
    val editorState by editorViewModel.uiState.collectAsState()

    // 提取核心数据
    // 注意：displayImage 可能来自于 Filter 结果，也可能就是原图，视业务需求而定。
    // 这里我们直接取 displayImage 作为切割源
    val sourceImage = editorState.displayImage?.image

    val project = segState.project
    val config = project?.config ?: SegmentationConfig()
    val results = project?.results ?: emptyList()
    val labels = project?.labels ?: emptyMap()
    val interaction = SegmentationInteractionWrapper(segState.selectedIndex, segState.isLabeling)

    // 本地状态
    val bigComposeBitmap = remember(sourceImage) { sourceImage?.toComposeImageBitmap() }
    val slicedCache = remember { mutableStateListOf<ImageBitmap?>() }

    // [新增] 监听取点事件 (替代原来的 awaitPointPick)
    LaunchedEffect(Unit) {
        editorViewModel.pickEvent.collect { event ->
            if (event is IntOffset) {
                // 用户在画布上点击了某个点，更新配置
                val newConfig = config.copy(startX = event.x, startY = event.y)
                segViewModel.runSegmentation(newConfig)
            }
        }
    }

    // 2. 异步切片逻辑 (完全保留原逻辑，保证列表流畅)
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
                    // 边界检查
                    if (rW > 0 && rH > 0 &&
                        rect.left >= 0 && rect.top >= 0 &&
                        (rect.left + rW) <= sourceImage.width &&
                        (rect.top + rH) <= sourceImage.height
                    ) {
                        val subView = sourceImage.getSubimage(rect.left, rect.top, rW, rH)
                        // 必须深拷贝，否则 subimage 共享 buffer 可能导致渲染问题
                        val copy = BufferedImage(rW, rH, BufferedImage.TYPE_INT_ARGB)
                        val g = copy.createGraphics()
                        g.drawImage(subView, 0, 0, null)
                        g.dispose()
                        newSlices.add(copy.toComposeImageBitmap())
                    } else {
                        newSlices.add(null)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
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
        // 3. 配置区域 (滚动)
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
                    // [修改] 不再挂起等待，而是设置模式，等待 SharedFlow 回调
                    editorViewModel.setPickingType(PickingType.POINT)
                }
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // 4. 结果网格
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
                    onSelectChar = { index -> segViewModel.selectRect(index) }
                )
            }

            // 底部按钮栏
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        if (sourceImage != null) {
                            // 收集有效数据
                            val batchItems = results.mapIndexedNotNull { index, rect ->
                                val label = labels[index]
                                if (!label.isNullOrBlank()) {
                                    rect to label
                                } else null
                            }

                            // [修改] 调用 FontLibraryViewModel 保存
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

        // 5. 标注弹窗
        if (interaction.isLabeling && interaction.selectedIndex in results.indices && bigComposeBitmap != null) {
            LabelingDialog(
                rect = results[interaction.selectedIndex],
                sourceImage = bigComposeBitmap,
                initialText = labels[interaction.selectedIndex] ?: "",
                onConfirm = { text -> segViewModel.submitLabel(text) },
                onDismiss = { segViewModel.dismissLabelDialog() }
            )
        }
    }
}

// 简单的辅助类，用于适配原来的参数结构，或者直接修改你的 SegmentationUiState
data class SegmentationInteractionWrapper(
    val selectedIndex: Int,
    val isLabeling: Boolean
)