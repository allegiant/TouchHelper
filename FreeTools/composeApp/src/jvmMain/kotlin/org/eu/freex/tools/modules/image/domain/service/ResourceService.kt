package org.eu.freex.tools.modules.image.domain.service

import kotlinx.coroutines.delay
import org.eu.freex.tools.modules.image.domain.model.WorkImage
import org.eu.freex.tools.modules.image.domain.repository.ImageRepository
import org.eu.freex.tools.common.utils.ImageUtils
import java.awt.image.BufferedImage
import java.io.File
import java.util.UUID

class ResourceService(
    private val repository: ImageRepository
) {
    suspend fun loadFile(file: File): Result<WorkImage> = runCatching {
        // 使用 ImageUtils.read 安全读取
        val image = ImageUtils.read(file) ?: throw Exception("无法加载图片或格式不支持: ${file.name}")
        WorkImage(
            id = UUID.randomUUID().toString(),
            name = file.name,
            bufferedImage = image,
            path = file.absolutePath
        )
    }

    suspend fun captureScreen(): Result<BufferedImage> {
        return try {
            delay(300)
            val image = ImageUtils.captureFullScreen()
            Result.success(image)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 【新增】支持导出
    suspend fun saveImage(image: BufferedImage, file: File): Result<Unit> = runCatching {
        ImageUtils.save(image, file)
    }
}