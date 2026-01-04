package org.eu.freex.tools.modules.image.domain.model

// --- 项目源状态 ---
data class Project(
    val sourceImages: List<WorkImage> = emptyList(),
    val selectedIndex: Int = -1,
) {
    val activeImage: WorkImage? get() = sourceImages.getOrNull(selectedIndex)
    val isEmpty: Boolean get() = sourceImages.isEmpty()

    /**
     * 添加一张源图并自动选中它
     */
    fun addSourceImage(image: WorkImage): Project {
        return copy(
            sourceImages = sourceImages + image,
            selectedIndex = sourceImages.size // 新图的 index (原size)
        )
    }

    /**
     * 选中指定索引（包含越界保护）
     */
    fun selectImage(index: Int): Project {
        if (index !in sourceImages.indices) return this
        return copy(selectedIndex = index)
    }

    /**
     * 删除指定索引的图片，并自动修正选中项
     */
    fun removeSourceImage(index: Int): Project {
        if (index !in sourceImages.indices) return this

        val newImages = sourceImages.toMutableList().apply { removeAt(index) }

        // 计算新的选中索引
        val newIndex = when {
            newImages.isEmpty() -> -1
            // 如果删除的是最后一项，或者当前选中项在删除项之后，索引需要前移
            selectedIndex >= newImages.size -> newImages.size - 1
            // 如果删除的是当前选中项之前的项，索引减一
            index < selectedIndex -> selectedIndex - 1
            // 其他情况，索引不变
            else -> selectedIndex
        }

        return copy(
            sourceImages = newImages,
            selectedIndex = newIndex.coerceAtLeast(0) // 确保不为-1 (除非空)
        )
    }
}