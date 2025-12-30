package org.eu.freex.tools.modules.image.domain.usecase

import org.eu.freex.tools.model.AppFilter
import org.eu.freex.tools.model.WorkImage
import org.eu.freex.tools.modules.image.data.repository.ProjectDatabase
import org.eu.freex.tools.utils.ImageUtils
import java.io.File

class ProjectProcessor() {
    suspend fun saveProject(
        file: File,
        sourceImages: List<WorkImage>,
        pipelineSteps: List<WorkImage>
    ): Result<Unit> = runCatching {
        val filters = pipelineSteps.mapNotNull { it.appliedFilter }
        ProjectDatabase.saveProject(file, sourceImages, filters)
    }

    data class LoadResult(val sourceImages: List<WorkImage>, val filters: List<AppFilter>)

    suspend fun loadProject(file: File): Result<LoadResult> = runCatching {
        val (paths, loadedSteps) = ProjectDatabase.loadProject(file)

        // 恢复源图片
        val sourceImages = paths.mapNotNull { path ->
            val f = File(path)
            if (f.exists()) {
                WorkImage(name = f.name, bufferedImage = ImageUtils.load(f))
            } else null
        }

        if (sourceImages.isEmpty()) throw Exception("工程中没有有效的源文件")

        // loadedSteps 这里其实就是 List<AppFilter> (根据你之前的描述)
        // 或者是包含了 Filter 信息的对象，这里假设 repository 返回的是 filters
        // 如果 ProjectDatabase 返回的是 filters，直接透传
        LoadResult(sourceImages, loadedSteps)
    }
}