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
    suspend fun applyFilter(source: BufferedImage, filter: ImageFilter): BufferedImage

    // 批量处理接口，用于优化性能
    suspend fun applyPipeline(baseImage: BufferedImage, filters: List<ImageFilter>): List<BufferedImage>

    // 切割计算接口
    suspend fun performSegmentation(image: BufferedImage, config: SegmentationConfig): Result<List<SegmentationRect>>
    suspend fun captureScreen(): BufferedImage
    suspend fun crop(source: BufferedImage, rect: Rect): BufferedImage
}