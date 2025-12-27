package org.eu.freex.tools.modules.image.domain.repository

import androidx.compose.ui.geometry.Rect
import org.eu.freex.tools.model.*
import java.io.File

interface ImageRepository {
    suspend fun loadFile(file: File): WorkImage?

    suspend fun applyFilter(
        source: WorkImage,
        filter: ImageFilter,
        params: Map<String, Any>
    ): WorkImage

    suspend fun segmentImage(
        source: WorkImage,
        isGridMode: Boolean,
        gridParams: GridParams,
        activeRules: List<ColorRule>
    ): Pair<List<Rect>, List<WorkImage>>
}