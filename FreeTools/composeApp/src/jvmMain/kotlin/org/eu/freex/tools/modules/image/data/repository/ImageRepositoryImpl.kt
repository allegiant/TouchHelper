package org.eu.freex.tools.modules.image.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eu.freex.tools.model.FilterConstants
import org.eu.freex.tools.model.GridParams
import org.eu.freex.tools.model.WorkImage
import org.eu.freex.tools.model.label
import org.eu.freex.tools.modules.image.data.source.RustDataSource
import org.eu.freex.tools.modules.image.domain.repository.ImageRepository
import org.eu.freex.tools.utils.ImageUtils
import uniffi.touch_core.ColorRule
import uniffi.touch_core.ImageFilter
import java.io.File
import javax.imageio.ImageIO

class ImageRepositoryImpl(
    private val dataSource: RustDataSource
) : ImageRepository {

    override suspend fun loadFile(file: File): WorkImage? = withContext(Dispatchers.IO) {
        try {
            val bufferedImage = ImageIO.read(file) ?: return@withContext null
            WorkImage(
                bufferedImage = bufferedImage,
                name = file.name
                // bitmap 字段已移除，由 UI 层按需生成
            )
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
        // 1. 获取原始像素 (极速)
        val pixels = ImageUtils.toRgbaPixels(source.bufferedImage)
        val width = source.bufferedImage.width
        val height = source.bufferedImage.height

        // 2. 解析参数
        val p1 = (params[FilterConstants.PARAM_MIN] as? Int)
        val p2 = (params[FilterConstants.PARAM_MAX] as? Int)
        // param3: boolean -> int (1=true, 0=false)
        val p3 = if (params[FilterConstants.PARAM_RGB_AVG] == true) 1 else 0

        // 3. 调用 Rust (通过 DataSource)
        val resultPixels = dataSource.applyFilter(
            pixels = pixels,
            width = width,
            height = height,
            filter = filter,
            param1 = p1,
            param2 = p2,
            param3 = p3
        )

        // 4. 还原图片
        val newImage = ImageUtils.fromRgbaPixels(width, height, resultPixels)

        WorkImage(
            bufferedImage = newImage,
            name = "${source.name}_${filter.label}",
            label = filter.label,
            params = params
        )
    }

    override suspend fun segmentImage(
        source: WorkImage,
        isGridMode: Boolean,
        gridParams: GridParams,
        activeRules: List<ColorRule>
    ): Pair<List<androidx.compose.ui.geometry.Rect>, List<WorkImage>> =
        withContext(Dispatchers.Default) {
            // 1. 获取原始像素
            val pixels = ImageUtils.toRgbaPixels(source.bufferedImage)
            val width = source.bufferedImage.width
            val height = source.bufferedImage.height

            // 2. 调用 Rust 扫描
            val rects = dataSource.scanComponents(
                pixels = pixels,
                width = width,
                height = height,
                rules = activeRules,
                isGridMode = isGridMode,
                gridRows = if (isGridMode) gridParams.rowCount else null,
                gridCols = if (isGridMode) gridParams.colCount else null
            )

            // 3. 转换结果类型 (Rust Rect -> Compose Rect)
            val composeRects = rects.map {
                androidx.compose.ui.geometry.Rect(
                    left = it.left.toFloat(),
                    top = it.top.toFloat(),
                    right = it.left.toFloat() + it.width.toFloat(),
                    bottom = it.top.toFloat() + it.height.toFloat(),
                )
            }

            // 4. 切割子图 (可选，如果 UI 不需要立即显示子图，可以懒加载)
            // 这里为了完整性，我们简单切割一下
            val subImages = composeRects.mapIndexed { index, rect ->
                val subImg = ImageUtils.cropImage(source.bufferedImage, rect)
                WorkImage(
                    bufferedImage = subImg,
                    name = "${source.name}_seg_$index"
                )
            }

            Pair(composeRects, subImages)
        }
}