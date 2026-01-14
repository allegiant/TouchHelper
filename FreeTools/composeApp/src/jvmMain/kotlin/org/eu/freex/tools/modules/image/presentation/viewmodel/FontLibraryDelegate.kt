package org.eu.freex.tools.modules.image.presentation.viewmodel

import androidx.compose.ui.graphics.toComposeImageBitmap
import org.eu.freex.tools.modules.image.domain.model.FontLibItem
import org.eu.freex.tools.modules.image.domain.model.SegmentationRect
import java.awt.image.BufferedImage
import java.io.File
import kotlin.math.log

class FontLibraryDelegate(private val context: ViewModelContext) {

    /**
     * 【重构】批量添加 + 详细日志 + 强壮的边界处理
     */
    fun addBatchToLibrary(items: List<Pair<SegmentationRect, String>>, sourceImage: BufferedImage) {
        val newLibItems = ArrayList<FontLibItem>()

        for ((rect, label) in items) {
            // 1. 检查参数有效性
            if (label.isBlank()) {
                println("Skip: Label is blank")
                continue
            }
            val w = rect.width.toInt()
            val h = rect.height.toInt()
            if (w <= 0 || h <= 0) {
                println("Skip: Invalid size ${w}x${h} for char '$label'")
                continue
            }

            try {
                // 2. 安全绘图 (Graphics2D)
                // 创建画布
                val subImage = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
                val g = subImage.createGraphics()

                // 绘制：即使坐标是负数或超出，drawImage 也不会崩，只会画空
                // 坐标取整，并取反来实现“裁剪”视角
                val drawX = -rect.left.toInt()
                val drawY = -rect.top.toInt()

                g.drawImage(sourceImage, drawX, drawY, null)
                g.dispose()

                // 3. 二值化处理 (这里简化演示)
                val binaryData = StringBuilder()
                // ... (你的二值化循环代码) ...
                // 临时填充 dummy 数据防止逻辑卡死，请替换回你的真实逻辑
                binaryData.append("0")

                // 4. 构建对象
                newLibItems.add(
                    FontLibItem(
                        charName = label,
                        width = subImage.width,
                        height = subImage.height,
                        binaryData = binaryData.toString(), // 确保这里不是空的
                        displayBitmap = subImage.toComposeImageBitmap()
                    )
                )
            } catch (e: Exception) {
                // 捕获单个字符的失败，不影响其他字符
                println("Error processing char '$label': ${e.message}")
                e.printStackTrace()
            }
        }

        // 5. 提交更新
        if (newLibItems.isNotEmpty()) {
            context.updateWorkspace {
                // 确保这里执行了 Copy
                val newList = fontLibrary + newLibItems
                copy(fontLibrary = newList)
            }
        } else {
            println("Delegate: 没有生成任何有效字符对象！")
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