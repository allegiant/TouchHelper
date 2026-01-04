package org.eu.freex.tools.modules.image.presentation.contract.model

import org.eu.freex.tools.modules.image.domain.model.WorkImage

// --- 项目源状态 ---
data class ProjectSession(
    val sourceImages: List<WorkImage> = emptyList(),
    val selectedSourceIndex: Int = -1,
) {
    val currentSourceImage: WorkImage?
        get() = sourceImages.getOrNull(selectedSourceIndex)
}