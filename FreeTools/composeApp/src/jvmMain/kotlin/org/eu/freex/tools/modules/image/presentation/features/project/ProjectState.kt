package org.eu.freex.tools.modules.image.presentation.features.project

import org.eu.freex.tools.modules.image.domain.model.WorkImage

// --- 项目源状态 ---
data class ProjectState(
    val sourceImages: List<WorkImage> = emptyList(),
    val selectedSourceIndex: Int = -1,
) {
    val currentSourceImage: WorkImage?
        get() = sourceImages.getOrNull(selectedSourceIndex)
}