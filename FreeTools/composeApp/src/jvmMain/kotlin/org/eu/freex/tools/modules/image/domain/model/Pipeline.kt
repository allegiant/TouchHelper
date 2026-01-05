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
): ImageEntity {
    // 获取当前生效的步骤图 (UI显示用)
    val activeOutputImage: WorkImage?
        get() = steps.getOrNull(activeIndex - 1)

    // 获取某一步骤的输入图
    fun getInputImage(stepIndex: Int, projectSource: WorkImage?): WorkImage? {
        return if (stepIndex <= 0) projectSource else steps.getOrNull(stepIndex - 1)
    }

    // 获取指定位置之后的所有滤镜 (用于重算)
    fun getFiltersAfter(index: Int): List<ImageFilter> {
        return steps.drop(index).mapNotNull { it.appliedFilter }
    }

    // =========================================================================
    // 高层业务动作 (High-level Business Actions)
    // =========================================================================

    /**
     * 【核心新增】激活/选中指定步骤
     * 自动计算该步骤对应的滤镜和输入底图，并进入编辑模式。
     * * @param index 目标步骤索引
     * @param projectSource 项目源图（用于计算第1步的输入）
     */
    fun activateStep(index: Int, projectSource: WorkImage?): Pipeline {
        // 1. 自动计算目标滤镜 (内聚逻辑)
        // 如果是第0步(原图)，就是查看模式；否则取出上一步应用的滤镜回显
        val targetFilter = if (index == 0) ViewFilter else
            steps.getOrNull(index - 1)?.appliedFilter ?: ViewFilter

        // 2. 自动计算输入底图 (内聚逻辑)
        val baseImage = getInputImage(index, projectSource)

        // 3. 变更状态
        return copy(
            activeIndex = index,
            draft = EditSession(activeFilter = targetFilter, baseImage = baseImage)
        )
    }

    // =========================================================================
    // 基础变异方法 (Basic Mutations)
    // =========================================================================

    /**
     * 更新草稿 (Preview/Draft)
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
     * 进入特定步骤的编辑模式 (底层方法，被 activateStep 调用或用于特定场景)
     */
    fun editStep(index: Int, filter: ImageFilter, baseImage: WorkImage?): Pipeline {
        return copy(
            activeIndex = index,
            draft = EditSession(activeFilter = filter, baseImage = baseImage)
        )
    }

    /**
     * 替换步骤链 (用于删除或修改中间步骤后的重算)
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