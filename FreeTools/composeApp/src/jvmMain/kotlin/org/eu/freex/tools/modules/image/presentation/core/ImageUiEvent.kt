package org.eu.freex.tools.modules.image.presentation.core

import androidx.compose.ui.geometry.Rect
import org.eu.freex.tools.modules.image.domain.model.ImageFilter
import org.eu.freex.tools.modules.image.domain.model.ImageLayer
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
// 【新增】删除资源事件
data class RemoveAsset(val assetId: String) : ImageUiEvent

// --- 截图与裁剪 ---
object StartScreenCapture : ImageUiEvent
data class ConfirmCrop(val sourceLayer: ImageLayer, val rect: Rect) : ImageUiEvent
object DismissCropper : ImageUiEvent

// --- 流水线 ---
data class SelectStep(val index: Int) : ImageUiEvent
data class PreviewFilter(val filter: ImageFilter) : ImageUiEvent
object ApplyNewStep : ImageUiEvent
object UpdateCurrentStep : ImageUiEvent

// --- 字库 ---
object StartFontMaker : ImageUiEvent