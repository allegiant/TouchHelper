package org.eu.freex.tools.modules.image.presentation.viewmodel

import org.eu.freex.tools.modules.image.domain.model.FontLibItem
import org.eu.freex.tools.modules.image.presentation.core.*

/**
 * ImageViewModel 的字体库管理扩展
 */
internal suspend fun ImageViewModel.handleFontLibraryEvent(event: FontLibraryEvent) {
    when (event) {
        is BatchAddToLibrary -> batchAddToLibrary(event)
        is DeleteFontItem -> deleteItem(event.id)
        is SortLibrary -> sortLibrary()
        is ClearLibrary -> clearLibrary()
        is ImportFontLibrary -> importLibrary(event.file)
        is ExportFontLibrary -> exportLibrary(event.file)
    }
}

// --- 私有业务逻辑 (请将原 Delegate 中的代码搬运至此) ---

private suspend fun ImageViewModel.batchAddToLibrary(event: BatchAddToLibrary) {
    val sourceImage = uiState.value.displayImage?.image ?: return

    useCase.addBatchToLibrary(getWorkspaceSnapshot(), event.items, sourceImage)
        .onSuccess { newWorkspace ->
            updateWorkspace { newWorkspace }
        }
}

private fun ImageViewModel.deleteItem(id: String) {
    updateWorkspace { it ->
        it.copy(fontLibrary = it.fontLibrary.filter { it.id != id })
    }
}

private fun ImageViewModel.sortLibrary() {
    updateWorkspace { it ->
        it.copy(fontLibrary = it.fontLibrary.sortedBy { it.charName })
    }

}

private fun ImageViewModel.clearLibrary() {
    updateWorkspace {
        it.copy(fontLibrary = emptyList())
    }
}

private suspend fun ImageViewModel.importLibrary(file: java.io.File) {
    if (!file.exists()) return
    val lines = file.readLines()
    val newItems = lines.mapNotNull { line ->
        try {
            val parts = line.split("$")
            if (parts.size >= 4) {
                val name = parts[0]
                val w = parts[1].toInt()
                val h = parts[2].toInt()
                val data = parts[3]
                // 注意：导入的数据通常没有 Bitmap 缓存，显示时可能需要重建或者显示占位符
                // 这里为了简单，displayBitmap 留空，UI层需处理 null 情况
                FontLibItem(charName = name, width = w, height = h, binaryData = data, displayBitmap = null)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    updateWorkspace {
        it.copy(fontLibrary = it.fontLibrary + newItems)
    }
}

private suspend fun ImageViewModel.exportLibrary(file: java.io.File) {
    useCase.exportFontLibrary(getWorkspaceSnapshot(), file)
        .onSuccess {
            println("成功导出")
        }
        .onFailure { e ->
            e.printStackTrace()
        }
}