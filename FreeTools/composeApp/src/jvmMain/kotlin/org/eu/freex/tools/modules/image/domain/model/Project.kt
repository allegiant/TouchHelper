package org.eu.freex.tools.modules.image.domain.model

data class Project(
    val sourceImages: List<WorkImage> = emptyList(),
    val selectedIndex: Int = -1,
): ImageEntity {
    val activeImage: WorkImage? get() = sourceImages.getOrNull(selectedIndex)
    val isEmpty: Boolean get() = sourceImages.isEmpty()

    // 业务行为：添加源图
    fun addSourceImage(image: WorkImage): Project {
        val newImages = sourceImages + image
        return copy(sourceImages = newImages, selectedIndex = newImages.lastIndex)
    }

    // 业务行为：移除源图
    fun removeSourceImage(index: Int): Project {
        if (index !in sourceImages.indices) return this
        val newImages = sourceImages.toMutableList().apply { removeAt(index) }
        val newIndex = when {
            newImages.isEmpty() -> -1
            selectedIndex >= newImages.size -> newImages.lastIndex
            else -> selectedIndex
        }
        return copy(sourceImages = newImages, selectedIndex = newIndex)
    }

    // 业务行为：选中源图
    fun selectImage(index: Int): Project {
        return if (index in sourceImages.indices) copy(selectedIndex = index) else this
    }
}