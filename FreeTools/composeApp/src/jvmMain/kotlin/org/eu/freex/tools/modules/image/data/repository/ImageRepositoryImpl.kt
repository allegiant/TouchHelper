package org.eu.freex.tools.modules.image.data.repository

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eu.freex.tools.model.ColorFilterType
import org.eu.freex.tools.model.ColorRule
import org.eu.freex.tools.model.GridParams
import org.eu.freex.tools.model.ImageFilter
import org.eu.freex.tools.model.WorkImage
import org.eu.freex.tools.modules.image.data.source.RustDataSource
import org.eu.freex.tools.modules.image.domain.repository.ImageRepository
import org.eu.freex.tools.utils.ImageUtils
import java.io.File
import javax.imageio.ImageIO

class ImageRepositoryImpl(
    private val rustDataSource: RustDataSource
) : ImageRepository {

    override suspend fun loadFile(file: File): WorkImage? = withContext(Dispatchers.IO) {
        try {
            val img = ImageIO.read(file) ?: return@withContext null
            WorkImage(img.toComposeImageBitmap(), img, file.name)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun applyFilter(
        source: WorkImage,
        filter: ImageFilter,
        params: Map<String, Any>
    ): WorkImage = withContext(Dispatchers.Default) {

        // 提取参数 (二值化需要 min/max)
        val p1 = (params["min"] as? Int)
        val p2 = (params["max"] as? Int)

        val processedImg = rustDataSource.applyFilter(source.bufferedImage, filter, p1, p2)

        WorkImage(
            bitmap = processedImg.toComposeImageBitmap(),
            bufferedImage = processedImg,
            name = source.name,
            label = filter.label, // 界面显示的步骤名
            isBinary = (filter == ColorFilterType.BINARIZATION),
            params = params
        )
    }

    override suspend fun segmentImage(
        source: WorkImage,
        isGridMode: Boolean,
        gridParams: GridParams,
        activeRules: List<ColorRule>
    ): Pair<List<Rect>, List<WorkImage>> =
        withContext(Dispatchers.Default) {

            // 1. 计算切割框
            val rects = if (isGridMode) {
                ImageUtils.generateGridRects(
                    gridParams.x, gridParams.y, gridParams.w, gridParams.h,
                    gridParams.colGap, gridParams.rowGap, gridParams.colCount, gridParams.rowCount
                )
            } else {
                rustDataSource.scanComponents(
                    source.bufferedImage,
                    activeRules.filter { it.isEnabled })
            }

            // 2. 裁剪生成小图
            val subImages = rects.mapIndexed { index, rect ->
                val cropped = ImageUtils.cropImage(source.bufferedImage, rect)
                WorkImage(
                    bitmap = cropped.toComposeImageBitmap(),
                    bufferedImage = cropped,
                    name = "${index}",
                    label = "${index}"
                )
            }

            rects to subImages
        }
}