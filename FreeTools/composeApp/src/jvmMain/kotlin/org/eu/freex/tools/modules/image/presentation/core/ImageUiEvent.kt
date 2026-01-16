package org.eu.freex.tools.modules.image.presentation.core

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import org.eu.freex.tools.modules.image.domain.model.ImageFilter
import org.eu.freex.tools.modules.image.domain.model.ImageLayer
import org.eu.freex.tools.modules.image.domain.model.SegmentationConfig
import org.eu.freex.tools.modules.image.domain.model.SegmentationRect
import java.io.File

sealed interface ImageUiEvent

// --- 1. 资源与项目事件组 (AssetEvent) ---
sealed interface AssetEvent : ImageUiEvent

data class LoadFile(val file: File) : AssetEvent
data class SaveProject(val file: File) : AssetEvent
data class LoadProject(val file: File) : AssetEvent
data class ExportDisplayImage(val file: File) : AssetEvent
data class ExportImage(val layer: ImageLayer, val file: File) : AssetEvent
data class SelectAsset(val assetId: String) : AssetEvent
data class RemoveAsset(val assetId: String) : AssetEvent

// --- 2. 交互与工具事件组 (InteractionEvent) ---
sealed interface InteractionEvent : ImageUiEvent
object StartScreenCapture : InteractionEvent
data class ConfirmCrop(val sourceLayer: ImageLayer, val rect: Rect) : InteractionEvent
object DismissCropper : InteractionEvent
data class TriggerColorPick(val color: Color) : InteractionEvent
data class TriggerPointPick(val point: IntOffset) : InteractionEvent
object CancelPick : InteractionEvent

// --- 3. 流水线与滤镜事件组 (PipelineEvent) ---
sealed interface PipelineEvent : ImageUiEvent
data class SelectStep(val index: Int) : PipelineEvent
data class RemoveStep(val index: Int) : PipelineEvent
data class PreviewFilter(val filter: ImageFilter) : PipelineEvent
object CancelPreview : PipelineEvent // 取消/清除预览
data class ApplyFilterStep(val filter: ImageFilter) : PipelineEvent
data class UpdateFilterStep(val filter: ImageFilter) : PipelineEvent

// --- 4. 切割与标注事件组 (SegmentationEvent) ---
sealed interface SegmentationEvent : ImageUiEvent
data class SwitchTab(val tab: WorkbenchTab) : SegmentationEvent
data class UpdateSegmentationConfig(val config: SegmentationConfig) : SegmentationEvent
data class SelectChar(val index: Int) : SegmentationEvent
data class SubmitLabelAndNext(val text: String) : SegmentationEvent
data object StopLabeling : SegmentationEvent

// --- 5. 字体库事件组 (FontLibraryEvent) ---
sealed interface FontLibraryEvent : ImageUiEvent

// 原有的 BatchAddToLibrary 移到这里 (注意：如果你之前的代码里引用了它，需要更新引用)
data class BatchAddToLibrary(val items: List<Pair<SegmentationRect, String>>) : FontLibraryEvent

// 新增的操作事件 (对应原 Delegate 的方法)
data class DeleteFontItem(val id: String) : FontLibraryEvent
data object SortLibrary : FontLibraryEvent
data object ClearLibrary : FontLibraryEvent
data class ImportFontLibrary(val file: File) : FontLibraryEvent
data class ExportFontLibrary(val file: File) : FontLibraryEvent