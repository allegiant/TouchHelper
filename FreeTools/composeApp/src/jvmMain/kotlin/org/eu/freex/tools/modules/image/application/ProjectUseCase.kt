package org.eu.freex.tools.modules.image.application

import org.eu.freex.tools.modules.image.domain.model.EditSession
import org.eu.freex.tools.modules.image.domain.model.Pipeline
import org.eu.freex.tools.modules.image.domain.model.Project
import org.eu.freex.tools.modules.image.domain.model.WorkImage
import org.eu.freex.tools.modules.image.domain.service.ProjectService
import org.eu.freex.tools.modules.image.domain.service.ResourceService
import java.awt.image.BufferedImage
import java.io.File
import java.util.UUID

class ProjectUseCase(
    private val projectService: ProjectService,
    private val resourceService: ResourceService
) {

    data class ProjectLoadResult(
        val project: Project,
        val pipeline: Pipeline
    )

    suspend fun loadProject(file: File): Result<ProjectLoadResult> = runCatching {
        val loadResult = projectService.loadProject(file).getOrThrow()

        val firstImage = loadResult.sourceImages.firstOrNull()
            ?: throw IllegalStateException("工程文件损坏：没有源图片")

        val newProject = Project(
            sourceImages = loadResult.sourceImages,
            selectedIndex = 0
        )

        // 构建占位符流水线
        val restoredSteps = loadResult.filters.map { filter ->
            WorkImage(
                bufferedImage = firstImage.bufferedImage, // 占位
                appliedFilter = filter,
                label = "待计算...",
                name = ""
            )
        }

        val newPipeline = Pipeline(
            steps = restoredSteps,
            activeIndex = restoredSteps.size,
            draft = EditSession()
        )

        ProjectLoadResult(newProject, newPipeline)
    }

    suspend fun saveProject(file: File, project: Project, pipeline: Pipeline): Result<Unit> {
        if (project.isEmpty) return Result.failure(IllegalStateException("无源文件"))
        return projectService.saveProject(file, project, pipeline.steps)
    }

    suspend fun importSourceFile(currentProject: Project, file: File): Result<Project> = runCatching {
        val newImage = resourceService.loadFile(file).getOrThrow()
        currentProject.addSourceImage(newImage)
    }

    suspend fun addCapturedImage(currentProject: Project, image: BufferedImage): Result<Project> = runCatching {
        val newWorkImage = WorkImage(
            id = UUID.randomUUID().toString(),
            bufferedImage = image,
            name = "Capture_${System.currentTimeMillis()}"
        )
        currentProject.addSourceImage(newWorkImage)
    }

    suspend fun captureScreen(): Result<BufferedImage> {
        return resourceService.captureScreen()
    }

    suspend fun exportImage(image: BufferedImage, file: File): Result<Unit> {
        return resourceService.saveImage(image, file)
    }
}