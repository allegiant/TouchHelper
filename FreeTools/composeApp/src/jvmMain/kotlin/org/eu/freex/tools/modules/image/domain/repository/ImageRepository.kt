package org.eu.freex.tools.modules.image.domain.repository

import androidx.compose.ui.geometry.Rect
import org.eu.freex.tools.model.AppFilter
import org.eu.freex.tools.model.AppSegmentation
import org.eu.freex.tools.model.GridParams
import org.eu.freex.tools.model.WorkImage
import uniffi.touch_core.ColorRule
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