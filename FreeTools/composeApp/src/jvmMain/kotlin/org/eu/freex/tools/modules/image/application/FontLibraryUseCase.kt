package org.eu.freex.tools.modules.image.application

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eu.freex.tools.common.utils.ImageUtils
import org.eu.freex.tools.modules.image.domain.model.FontLibItem
import org.eu.freex.tools.modules.image.domain.model.SegmentationRect
import org.eu.freex.tools.modules.image.domain.model.toComposeRect
import org.eu.freex.tools.modules.image.domain.repository.ProjectRepository
import uniffi.touch_core.fontExtractFeature
import java.io.File

class FontLibraryUseCase(
    private val projectRepo: ProjectRepository
) {
    // 批量添加 + 智能二值化 + 自动去重
    suspend fun addBatchToLibrary(items: List<Pair<SegmentationRect, String>>) = withContext(Dispatchers.Default) {
        val workspace = projectRepo.workspace.value
        val sourceImage = workspace.displayImage?.image ?: return@withContext

        // 1. 获取现有字库的特征指纹集合 (用于去重)
        // 使用 MutableSet，不仅能查现有字库，还能防止当前批次内有重复项
        val existingSignatures = workspace.fontLibrary.map { it.binaryData }.toMutableSet()

        val newLibItems = ArrayList<FontLibItem>()
        var duplicateCount = 0

        for ((rect, label) in items) {
            if (label.isBlank()) continue
            val w = rect.width.toInt()
            val h = rect.height.toInt()
            if (w <= 0 || h <= 0) continue

            try {
                // 2. 裁剪图片
                val subImage = ImageUtils.cropImage(sourceImage, rect.toComposeRect())
                val pixels = ImageUtils.toRgbaPixels(subImage)


                // 3. 生成二值化特征串 (使用统一工具，保证与识别算法一致)
                val binaryData = fontExtractFeature(pixels, subImage.width, subImage.height)

                // 4. 🌟【核心修复】去重检查
                if (existingSignatures.contains(binaryData)) {
                    duplicateCount++
                    continue // 如果特征串已存在，直接跳过
                }

                // 5. 将新特征加入集合 (防止批次内部自我重复)
                existingSignatures.add(binaryData)

                // 6. 生成预览图并添加到待添加列表
                // 注意：这里使用 ImageUtils.binaryStringToBitmap 或 subImage.toComposeImageBitmap 都可以
                // 建议直接用 subImage 转，为了性能和显示效果
                newLibItems.add(
                    FontLibItem(
                        charName = label,
                        width = w,
                        height = h,
                        binaryData = binaryData,
                        displayBitmap = subImage.toComposeImageBitmap()
                    )
                )

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 7. 只有当有新项目时才更新仓库
        if (newLibItems.isNotEmpty()) {
            projectRepo.updateWorkspace { ws ->
                ws.copy(fontLibrary = ws.fontLibrary + newLibItems)
            }
            println("成功添加 ${newLibItems.size} 个字模，跳过 $duplicateCount 个重复项")
        } else {
            println("未添加任何字模 (重复: $duplicateCount, 无效: ${items.size - duplicateCount})")
        }
    }

    suspend fun exportLibrary(file: File) = withContext(Dispatchers.IO) {
        val lib = projectRepo.workspace.value.fontLibrary
        val content = StringBuilder()
        lib.forEach { item ->
            content.append("${item.charName}$${item.width}$${item.height}$${item.binaryData}\n")
        }
        file.writeText(content.toString())
    }
}