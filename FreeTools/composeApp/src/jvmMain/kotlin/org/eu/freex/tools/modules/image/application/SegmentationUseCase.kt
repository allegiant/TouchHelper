package org.eu.freex.tools.modules.image.application

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eu.freex.tools.modules.image.domain.model.SegmentationConfig
import org.eu.freex.tools.modules.image.domain.model.SegmentationProject
import org.eu.freex.tools.modules.image.domain.repository.LayerRepository
import org.eu.freex.tools.modules.image.domain.repository.ProjectRepository

class SegmentationUseCase(
    private val projectRepo: ProjectRepository,
    private val layerRepo: LayerRepository
) {
    suspend fun runSegmentation(config: SegmentationConfig): Result<Unit> = withContext(Dispatchers.Default) {
        val workspace = projectRepo.workspace.value
        val sourceImage = workspace.displayImage?.image ?: return@withContext Result.failure(Exception("No image"))

        layerRepo.performSegmentation(sourceImage, config).map { rects ->
            projectRepo.updateWorkspace { ws ->
                val newProject = (ws.segmentation ?: SegmentationProject()).copy(
                    results = rects,
                    config = config
                )
                ws.copy(segmentation = newProject)
            }
        }
    }
}