package org.eu.freex.tools.modules.image.domain.repository

import androidx.compose.ui.geometry.Rect
import org.eu.freex.tools.modules.image.domain.model.AppFilter
import org.eu.freex.tools.modules.image.domain.model.AppSegmentation
import org.eu.freex.tools.modules.image.domain.model.WorkImage
import java.io.File

interface ImageRepository {
    suspend fun loadFile(file: File): WorkImage?

    suspend fun applyFilter(
        source: WorkImage,
        filter: AppFilter,
    ): WorkImage

    suspend fun segmentImage(
        source: WorkImage,
        segmentation: AppSegmentation
    ): Pair<List<Rect>, List<WorkImage>>
}