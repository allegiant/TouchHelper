package org.eu.freex.tools.modules.image.presentation.viewmodel

import androidx.compose.ui.graphics.toComposeImageBitmap
import org.eu.freex.tools.modules.image.domain.model.FontLibItem
import org.eu.freex.tools.modules.image.domain.model.SegmentationRect
import java.awt.image.BufferedImage
import java.io.File

class FontLibraryDelegate(private val context: ViewModelContext) {

    /**
     * 将切割好的结果添加到字库
     */
    fun addToLibrary(rect: SegmentationRect, sourceImage: BufferedImage, label: String) {
        if (label.isBlank()) return

        // --- 修复方案：使用 Graphics2D 安全裁剪，避免 getSubimage 越界崩溃 ---

        // 1. 获取目标尺寸 (直接使用切割框的大小)
        val targetWidth = rect.width.toInt()
        val targetHeight = rect.height.toInt()

        if (targetWidth <= 0 || targetHeight <= 0) return

        // 2. 创建一个新的 BufferedImage 用于存放切割结果
        // 使用 ARGB 以支持透明度，防止背景变黑
        val subImage = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB)

        // 3. 将原图绘制到新图上，通过坐标偏移实现“切割”
        // g.drawImage 会自动处理边界，如果原图坐标超出范围，只会绘制重叠部分，绝不会崩
        val g = subImage.createGraphics()
        try {
            // 注意：这里将原图向左上移动 rect.left/rect.top 距离，
            // 这样 (0,0) 位置显示的正好是原图 (rect.left, rect.top) 的像素
            g.drawImage(sourceImage, -rect.left, -rect.top, null)
        } finally {
            g.dispose()
        }

        // 4. 生成二值化数据
        val binaryData = StringBuilder()
        for (y in 0 until subImage.height) {
            for (x in 0 until subImage.width) {
                val pixel = subImage.getRGB(x, y)
                val alpha = (pixel shr 24) and 0xff
                val red = (pixel shr 16) and 0xff
                // 简单逻辑：非透明且较暗的像素视为前景(1)
                binaryData.append(if (alpha > 0 && red < 128) "1" else "0")
            }
        }

        // 5. 创建对象
        val newItem = FontLibItem(
            charName = label,
            width = subImage.width,
            height = subImage.height,
            binaryData = binaryData.toString(),
            displayBitmap = subImage.toComposeImageBitmap()
        )

        // 6. 更新状态并打印日志（如果有控制台的话可以看到）
        println("Adding to library: $label (${newItem.width}x${newItem.height})")
        context.updateWorkspace {
            copy(fontLibrary = fontLibrary + newItem)
        }
    }

    fun deleteItem(id: String) {
        context.updateWorkspace {
            copy(fontLibrary = fontLibrary.filter { it.id != id })
        }
    }

    fun sortLibrary() {
        context.updateWorkspace {
            copy(fontLibrary = fontLibrary.sortedBy { it.charName })
        }
    }

    fun clearLibrary() {
        context.updateWorkspace {
            copy(fontLibrary = emptyList())
        }
    }

    /**
     * 导出字库 (兼容 Vue 工具的格式: char$w$h$data)
     */
    fun exportLibrary(file: File) {
        val content = StringBuilder()
        context.getWorkspaceSnapshot().fontLibrary.forEach { item ->
            content.append("${item.charName}$${item.width}$${item.height}$${item.binaryData}\n")
        }
        file.writeText(content.toString())
    }

    /**
     * 导入字库
     */
    fun importLibrary(file: File) {
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

        context.updateWorkspace {
            copy(fontLibrary = fontLibrary + newItems)
        }
    }
}