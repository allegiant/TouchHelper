package org.eu.freex.tools.modules.image.presentation.core

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import org.eu.freex.tools.modules.image.domain.model.ImageFilter
import org.eu.freex.tools.modules.image.domain.model.ImageLayer
import org.eu.freex.tools.modules.image.domain.model.SegmentationConfig
import java.io.File

sealed interface ImageUiEvent

// --- 文件与资源 ---
data class LoadFile(val file: File) : ImageUiEvent
data class SaveProject(val file: File) : ImageUiEvent
data class LoadProject(val file: File) : ImageUiEvent

// 导出
data class ExportDisplayImage(val file: File) : ImageUiEvent
data class ExportImage(val layer: ImageLayer, val file: File) : ImageUiEvent

// 资源管理
data class SelectAsset(val assetId: String) : ImageUiEvent
data class RemoveAsset(val assetId: String) : ImageUiEvent

// --- 截图与裁剪 ---
object StartScreenCapture : ImageUiEvent
data class ConfirmCrop(val sourceLayer: ImageLayer, val rect: Rect) : ImageUiEvent
object DismissCropper : ImageUiEvent

// --- 取色器 ---
data class TriggerColorPick(val color: Color) : ImageUiEvent
object CancelColorPick : ImageUiEvent

// --- 流水线 ---
data class SelectStep(val index: Int) : ImageUiEvent
data class PreviewFilter(val filter: ImageFilter) : ImageUiEvent
object CancelPreview : ImageUiEvent // 取消/清除预览


data class ApplyFilterStep(val filter: ImageFilter) : ImageUiEvent
data class UpdateFilterStep(val filter: ImageFilter) : ImageUiEvent

// --- [新增] 切割交互事件 ---
data class SwitchTab(val tab: WorkbenchTab) : ImageUiEvent
data class UpdateSegmentationConfig(val config: SegmentationConfig) : ImageUiEvent
data class SelectChar(val index: Int) : ImageUiEvent
data class SubmitLabelAndNext(val text: String) : ImageUiEvent
data object StopLabeling : ImageUiEvent