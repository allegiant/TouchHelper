package org.eu.freex.tools.modules.image.domain.model

// --- 项目源状态 ---
data class Project(
    val sourceImages: List<WorkImage> = emptyList(),
    val selectedIndex: Int = -1,
) {
    val activeImage: WorkImage? get() = sourceImages.getOrNull(selectedIndex)
    val isEmpty: Boolean get() = sourceImages.isEmpty()
}