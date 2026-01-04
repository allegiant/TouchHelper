package org.eu.freex.tools.modules.image.domain.service

import kotlinx.coroutines.delay
import org.eu.freex.tools.modules.image.domain.model.WorkImage
import org.eu.freex.tools.modules.image.domain.repository.ImageRepository
import org.eu.freex.tools.common.utils.ImageUtils
import java.awt.image.BufferedImage
import java.io.File

/**
 * 资源逻辑处理器 (Pure Use Case)
 * 职责：单纯负责加载和生成 WorkImage 对象
 */
class ResourceService(
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

    /**
     * 截取全屏
     * 【重构】现在返回 Result<BufferedImage>，不生成 WorkImage。
     * 这样 UI 层 (ScreenCropperDialog) 可以暂持这份原始数据用于裁剪，
     * 只有在确认裁剪后，才由 ViewModel 生成最终的 WorkImage 存入工程。
     */
    suspend fun captureScreen(): Result<BufferedImage> {
        // 使用 try-catch 替代 runCatching 以支持 suspend 函数 (delay)
        return try {
            // 给 UI 隐藏窗口的时间 (使用协程挂起，不阻塞线程)
            delay(300)
            val image = ImageUtils.captureFullScreen()
            Result.success(image)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}