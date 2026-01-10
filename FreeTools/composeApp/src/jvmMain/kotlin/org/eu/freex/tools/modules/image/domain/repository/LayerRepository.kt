package org.eu.freex.tools.modules.image.domain.repository

import androidx.compose.ui.geometry.Rect
import org.eu.freex.tools.modules.image.domain.model.ImageFilter
import org.eu.freex.tools.modules.image.domain.model.SegmentationConfig
import org.eu.freex.tools.modules.image.domain.model.SegmentationRect
import java.awt.image.BufferedImage
import java.io.File

interface LayerRepository {
    suspend fun loadFromFile(file: File): BufferedImage
    suspend fun saveToFile(image: BufferedImage, file: File)
    suspend fun captureScreen(): BufferedImage
    suspend fun applyFilter(source: BufferedImage, filter: ImageFilter): BufferedImage
    suspend fun crop(source: BufferedImage, rect: Rect): BufferedImage

    // [新增] 切割计算接口
    // 输入: BufferedImage (原图), Domain Config (领域配置)
    // 输出: Domain Rects (领域坐标)
    suspend fun performSegmentation(image: BufferedImage, config: SegmentationConfig): Result<List<SegmentationRect>>
}