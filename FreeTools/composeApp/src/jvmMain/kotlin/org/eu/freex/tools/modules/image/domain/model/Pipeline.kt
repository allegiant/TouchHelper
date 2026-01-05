package org.eu.freex.tools.modules.image.domain.model

data class EditSession(
    val activeFilter: ImageFilter = ViewFilter,
    val previewImage: WorkImage? = null,
    val baseImage: WorkImage? = null
)

data class Pipeline(
    val steps: List<WorkImage> = emptyList(),
    val activeIndex: Int = 0,
    val draft: EditSession = EditSession()
) {
    // 获取当前生效的步骤图
    val activeOutputImage: WorkImage?
        get() = steps.getOrNull(activeIndex - 1)

    // 获取某一步骤的输入图
    fun getInputImage(stepIndex: Int, projectSource: WorkImage?): WorkImage? {
        return if (stepIndex <= 0) projectSource else steps.getOrNull(stepIndex - 1)
    }

    fun getFiltersAfter(index: Int): List<ImageFilter> {
        return steps.drop(index).mapNotNull { it.appliedFilter }
    }

    // =========================================================================
    // 语义化变异方法 (Semantic Mutations) - 解决嵌套 Copy 地狱
    // =========================================================================

    /**
     * 更新草稿 (Preview/Draft)
     * 用于滤镜参数调节时的实时反馈
     */
    fun updateDraft(
        previewImage: WorkImage? = draft.previewImage,
        activeFilter: ImageFilter = draft.activeFilter,
        baseImage: WorkImage? = draft.baseImage
    ): Pipeline {
        return copy(
            draft = draft.copy(
                previewImage = previewImage,
                activeFilter = activeFilter,
                baseImage = baseImage
            )
        )
    }

    /**
     * 进入特定步骤的编辑模式
     * 移动指针，并初始化草稿
     */
    fun editStep(index: Int, filter: ImageFilter, baseImage: WorkImage?): Pipeline {
        return copy(
            activeIndex = index,
            draft = EditSession(activeFilter = filter, baseImage = baseImage)
        )
    }

    /**
     * 替换步骤链 (用于删除或修改中间步骤后的重算)
     * 修改后自动清空草稿
     */
    fun replaceSteps(startIndex: Int, newSuffix: List<WorkImage>): Pipeline {
        val prefix = steps.take(startIndex)
        val newSteps = prefix + newSuffix
        return copy(
            steps = newSteps,
            activeIndex = prefix.size + 1, // 焦点移动到被修改的位置
            draft = EditSession() // 提交后重置草稿
        )
    }

    /**
     * 追加新步骤
     * 追加后自动清空草稿
     */
    fun appendStep(newImage: WorkImage): Pipeline {
        val newSteps = steps.take(activeIndex) + newImage
        return copy(
            steps = newSteps,
            activeIndex = newSteps.size,
            draft = EditSession()
        )
    }
}