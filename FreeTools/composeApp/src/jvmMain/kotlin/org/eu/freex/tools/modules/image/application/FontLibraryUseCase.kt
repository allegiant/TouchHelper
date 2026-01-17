package org.eu.freex.tools.modules.image.application

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eu.freex.tools.common.utils.ImageUtils
import org.eu.freex.tools.modules.image.domain.model.FontLibItem
import org.eu.freex.tools.modules.image.domain.model.SegmentationRect
import org.eu.freex.tools.modules.image.domain.repository.ProjectRepository
import java.awt.image.BufferedImage
import java.io.File
import java.util.ArrayList

class FontLibraryUseCase(
    private val projectRepo: ProjectRepository
) {
    // 批量添加 + 智能二值化
    suspend fun addBatchToLibrary(items: List<Pair<SegmentationRect, String>>) = withContext(Dispatchers.Default) {
        val workspace = projectRepo.workspace.value
        val sourceImage = workspace.displayImage?.image ?: return@withContext

        val newLibItems = ArrayList<FontLibItem>()

        for ((rect, label) in items) {
            if (label.isBlank()) continue
            val w = rect.width.toInt()
            val h = rect.height.toInt()
            if (w <= 0 || h <= 0) continue

            try {
                // 1. 裁剪
                val subImage = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
                val g = subImage.createGraphics()
                g.drawImage(sourceImage, -rect.left.toInt(), -rect.top.toInt(), null)
                g.dispose()

                // 2. 统计亮度
                var totalBrightness = 0L
                var pixelCount = 0
                val width = subImage.width
                val height = subImage.height
                val pixels = IntArray(width * height)
                subImage.getRGB(0, 0, width, height, pixels, 0, width)

                for (pixel in pixels) {
                    val r = (pixel shr 16) and 0xff
                    val gVal = (pixel shr 8) and 0xff
                    val b = pixel and 0xff
                    totalBrightness += (r * 0.299 + gVal * 0.587 + b * 0.114).toLong()
                    pixelCount++
                }
                val avgBrightness = if (pixelCount > 0) totalBrightness / pixelCount else 128
                val isDarkBackground = avgBrightness < 128

                // 3. 生成二值化数据
                val binaryData = StringBuilder()
                var oneCount = 0
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val pixel = subImage.getRGB(x, y)
                        val alpha = (pixel shr 24) and 0xff
                        val r = (pixel shr 16) and 0xff
                        val gVal = (pixel shr 8) and 0xff
                        val b = pixel and 0xff
                        val luma = (r * 0.299 + gVal * 0.587 + b * 0.114).toInt()

                        val isForeground = if (alpha < 50) false
                        else if (isDarkBackground) luma > avgBrightness + 20
                        else luma < avgBrightness - 20

                        if (isForeground) {
                            binaryData.append("1")
                            oneCount++
                        } else {
                            binaryData.append("0")
                        }
                    }
                }

                if (oneCount > 0) {
                    newLibItems.add(
                        FontLibItem(
                            charName = label,
                            width = subImage.width,
                            height = subImage.height,
                            binaryData = binaryData.toString(),
                            displayBitmap = subImage.toComposeImageBitmap()
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (newLibItems.isNotEmpty()) {
            projectRepo.updateWorkspace { ws ->
                ws.copy(fontLibrary = ws.fontLibrary + newLibItems)
            }
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