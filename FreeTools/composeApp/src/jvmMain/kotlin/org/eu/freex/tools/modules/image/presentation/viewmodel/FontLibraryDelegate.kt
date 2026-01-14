package org.eu.freex.tools.modules.image.presentation.viewmodel

import androidx.compose.ui.graphics.ImageBitmap
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

        // 1. 切图
        val subImage = sourceImage.getSubimage(rect.left, rect.top, rect.width.toInt(), rect.height.toInt())

        // 2. 生成二值化数据 (这里简化处理，假设 pipeline 已经处理过或者直接取亮度)
        // 为了演示，这里使用简单的阈值提取 01 串
        val binaryData = StringBuilder()
        for (y in 0 until subImage.height) {
            for (x in 0 until subImage.width) {
                val pixel = subImage.getRGB(x, y)
                // 简单判断：非透明且非纯黑/白 (根据你的二值化逻辑调整)
                // 这里假设 alpha=0 是背景，或者白色是背景
                val alpha = (pixel shr 24) and 0xff
                val red = (pixel shr 16) and 0xff
                // 简单逻辑：有颜色即为1
                binaryData.append(if (alpha > 0 && red < 128) "1" else "0")
            }
        }

        // 3. 创建对象
        val newItem = FontLibItem(
            charName = label,
            width = subImage.width,
            height = subImage.height,
            binaryData = binaryData.toString(),
            displayBitmap = subImage.toComposeImageBitmap()
        )

        // 4. 更新状态
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