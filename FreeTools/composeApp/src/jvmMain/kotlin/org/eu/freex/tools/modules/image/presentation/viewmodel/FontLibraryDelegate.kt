package org.eu.freex.tools.modules.image.presentation.viewmodel

import androidx.compose.ui.graphics.toComposeImageBitmap
import org.eu.freex.tools.modules.image.domain.model.FontLibItem
import org.eu.freex.tools.modules.image.domain.model.SegmentationRect
import java.awt.image.BufferedImage
import java.io.File

class FontLibraryDelegate(private val context: ViewModelContext) {

    /**
     * 【修复版】批量添加 + 智能二值化 (自动识别白字/黑字)
     */
    fun addBatchToLibrary(items: List<Pair<SegmentationRect, String>>, sourceImage: BufferedImage) {
        val newLibItems = ArrayList<FontLibItem>()

        for ((rect, label) in items) {
            // 1. 检查参数有效性
            if (label.isBlank()) continue
            val w = rect.width.toInt()
            val h = rect.height.toInt()
            if (w <= 0 || h <= 0) continue

            try {
                // 2. 安全绘图 (Graphics2D)
                val subImage = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
                val g = subImage.createGraphics()
                // 坐标取整并取反，实现裁剪
                val drawX = -rect.left.toInt()
                val drawY = -rect.top.toInt()
                g.drawImage(sourceImage, drawX, drawY, null)
                g.dispose()

                // 3. 智能二值化 (Smart Binarization)
                // 先统计平均亮度，判断是"白底黑字"还是"黑底白字"
                var totalBrightness = 0L
                var pixelCount = 0
                for (y in 0 until subImage.height) {
                    for (x in 0 until subImage.width) {
                        val pixel = subImage.getRGB(x, y)
                        val r = (pixel shr 16) and 0xff
                        val gVal = (pixel shr 8) and 0xff
                        val b = pixel and 0xff
                        // 亮度公式
                        totalBrightness += (r * 0.299 + gVal * 0.587 + b * 0.114).toLong()
                        pixelCount++
                    }
                }

                // 默认为128，防止除以0
                val avgBrightness = if (pixelCount > 0) totalBrightness / pixelCount else 128

                // 如果平均亮度较低(<128)，说明背景是黑的，文字是亮的 -> 阈值条件：像素 > 平均值
                // 如果平均亮度较高(>128)，说明背景是白的，文字是暗的 -> 阈值条件：像素 < 平均值
                val isDarkBackground = avgBrightness < 128

                val binaryData = StringBuilder()
                var oneCount = 0

                for (y in 0 until subImage.height) {
                    for (x in 0 until subImage.width) {
                        val pixel = subImage.getRGB(x, y)
                        val alpha = (pixel shr 24) and 0xff
                        val r = (pixel shr 16) and 0xff
                        val gVal = (pixel shr 8) and 0xff
                        val b = pixel and 0xff
                        val luma = (r * 0.299 + gVal * 0.587 + b * 0.114).toInt()

                        // 判断是否为前景(1)
                        // 逻辑：必须不透明(alpha>50)，且亮度符合前景特征
                        val isForeground = if (alpha < 50) {
                            false
                        } else if (isDarkBackground) {
                            luma > avgBrightness + 20 // 黑底：比背景亮的是字
                        } else {
                            luma < avgBrightness - 20 // 白底：比背景暗的是字
                        }

                        if (isForeground) {
                            binaryData.append("1")
                            oneCount++
                        } else {
                            binaryData.append("0")
                        }
                    }
                }

                // 如果全是0，打印警告
                if (oneCount == 0) {
                    println("Warning: Char '$label' produced empty binary data! (AvgLuma: $avgBrightness)")
                }

                // 4. 构建对象
                newLibItems.add(
                    FontLibItem(
                        charName = label,
                        width = subImage.width,
                        height = subImage.height,
                        binaryData = binaryData.toString(),
                        displayBitmap = subImage.toComposeImageBitmap() // 刚添加时直接用原图显示
                    )
                )
            } catch (e: Exception) {
                println("Error processing char '$label': ${e.message}")
                e.printStackTrace()
            }
        }

        // 5. 提交更新
        if (newLibItems.isNotEmpty()) {
            context.updateWorkspace {
                copy(fontLibrary = fontLibrary + newLibItems)
            }
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