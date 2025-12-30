package org.eu.freex.tools.modules.image.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eu.freex.tools.modules.image.domain.model.AppFilter
import org.eu.freex.tools.modules.image.domain.model.AppSegmentation
import org.eu.freex.tools.modules.image.domain.model.WorkImage
import org.eu.freex.tools.modules.image.domain.repository.ImageRepository
import org.eu.freex.tools.common.utils.ImageUtils
import java.io.File
import javax.imageio.ImageIO

class ImageRepositoryImpl : ImageRepository {

    override suspend fun loadFile(file: File): WorkImage? = withContext(Dispatchers.IO) {
        try {
            val bufferedImage = ImageIO.read(file) ?: return@withContext null
            WorkImage(
                bufferedImage = bufferedImage,
                name = file.name
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // 【修改】移除 params Map，直接使用 filter 对象
    override suspend fun applyFilter(
        source: WorkImage,
        filter: AppFilter
    ): WorkImage = withContext(Dispatchers.Default) {
        // 1. 获取原始像素
        val pixels = ImageUtils.toRgbaPixels(source.bufferedImage)
        val width = source.bufferedImage.width
        val height = source.bufferedImage.height
        val resultPixels = filter.apply(pixels, width, height)
        // 3. 还原图片
        val newImage = ImageUtils.fromRgbaPixels(width, height, resultPixels)

        // 4. 返回结果
        WorkImage(
            bufferedImage = newImage,
            name = "${source.name}_${filter.name}",
            label = filter.name,
            appliedFilter = filter
        )
    }

    override suspend fun segmentImage(
        source: WorkImage,
        segmentation: AppSegmentation
    ): Pair<List<androidx.compose.ui.geometry.Rect>, List<WorkImage>> =
        withContext(Dispatchers.Default) {
            val pixels = ImageUtils.toRgbaPixels(source.bufferedImage)
            val width = source.bufferedImage.width
            val height = source.bufferedImage.height
            val rustRects = segmentation.segment(pixels, width, height)

            val composeRects = rustRects.map {
                androidx.compose.ui.geometry.Rect(
                    left = it.left.toFloat(),
                    top = it.top.toFloat(),
                    right = it.left.toFloat() + it.width.toFloat(),
                    bottom = it.top.toFloat() + it.height.toFloat(),
                )
            }

            val subImages = composeRects.mapIndexed { index, rect ->
                val subImg = ImageUtils.cropImage(source.bufferedImage, rect)
                WorkImage(
                    bufferedImage = subImg,
                    name = "${source.name}_seg_$index",
                )
            }

            Pair(composeRects, subImages)
        }
}