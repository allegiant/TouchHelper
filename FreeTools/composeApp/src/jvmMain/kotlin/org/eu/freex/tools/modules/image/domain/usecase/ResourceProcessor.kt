package org.eu.freex.tools.modules.image.domain.usecase

import org.eu.freex.tools.modules.image.domain.model.WorkImage
import org.eu.freex.tools.modules.image.domain.repository.ImageRepository
import org.eu.freex.tools.common.utils.ImageUtils
import java.io.File

/**
 * 资源逻辑处理器 (Pure Use Case)
 * 职责：单纯负责加载和生成 WorkImage 对象
 */
class ResourceProcessor(
    private val repository: ImageRepository
) {
    /**
     * 加载文件
     */
    suspend fun loadFile(file: File): Result<WorkImage> = runCatching {
        repository.loadFile(file) ?: throw Exception("无法加载图片或格式不支持: ${file.name}")
    }

    // 如果你有截图裁剪的逻辑，且该逻辑包含复杂的 Repo 调用，也可以放这里
    // 简单的 ImageUtils 调用可以直接在 ViewModel 做，或者封装在这里
    /*
    suspend fun processCrop(source: BufferedImage, rect: Rect): Result<WorkImage> = runCatching {
        val cropped = ImageUtils.cropImage(source, rect)
        WorkImage(bufferedImage = cropped, name = "crop_${System.currentTimeMillis()}")
    }
    */

    suspend fun captureScreen(): Result<WorkImage> = runCatching {
        // 延迟给 UI 隐藏的时间
        Thread.sleep(300)
        val image = ImageUtils.captureFullScreen()
        WorkImage(bufferedImage = image, name = "ScreenCapture_${System.currentTimeMillis()}")
    }
}