package org.eu.freex.tools.modules.image.domain.repository

import androidx.compose.ui.geometry.Rect
import org.eu.freex.tools.modules.image.domain.model.ImageFilter
import org.eu.freex.tools.modules.image.domain.model.Segmentation
import org.eu.freex.tools.modules.image.domain.model.WorkImage
import java.io.File

interface ImageRepository {
    suspend fun loadFile(file: File): WorkImage?

    suspend fun applyFilter(
        source: WorkImage,
        filter: ImageFilter,
    ): WorkImage

    suspend fun segmentImage(
        source: WorkImage,
        segmentation: Segmentation
    ): Pair<List<Rect>, List<WorkImage>>
}