package org.eu.freex.tools.modules.image.domain.repository

import org.eu.freex.tools.modules.image.domain.model.ImageFilter
import org.eu.freex.tools.modules.image.domain.model.Segmentation
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.File

interface LayerRepository {
    suspend fun loadFromFile(file: File): BufferedImage
    suspend fun saveToFile(image: BufferedImage, file: File)
    suspend fun captureScreen(): BufferedImage
    suspend fun applyFilter(source: BufferedImage, filter: ImageFilter): BufferedImage
    suspend fun segment(source: BufferedImage, segmentation: Segmentation): List<Rectangle>
    suspend fun crop(source: BufferedImage, rect: Rectangle): BufferedImage
}