package org.eu.freex.tools.modules.image.domain.repository

import androidx.compose.ui.geometry.Rect
import org.eu.freex.tools.modules.image.domain.model.ImageFilter
import org.eu.freex.tools.modules.image.domain.model.Segmentation
import java.awt.image.BufferedImage
import java.io.File

interface LayerRepository {
    suspend fun loadFromFile(file: File): BufferedImage
    suspend fun saveToFile(image: BufferedImage, file: File)
    suspend fun captureScreen(): BufferedImage
    suspend fun applyFilter(source: BufferedImage, filter: ImageFilter): BufferedImage
    suspend fun segment(source: BufferedImage, segmentation: Segmentation): List<Rect>
    suspend fun crop(source: BufferedImage, rect: Rect): BufferedImage
}