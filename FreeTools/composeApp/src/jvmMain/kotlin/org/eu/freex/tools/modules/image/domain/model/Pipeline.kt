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
    // 1. 获取当前用于显示的最终图片
    val activeOutputImage: WorkImage?
        get() = steps.getOrNull(activeIndex - 1)

    // 2. 获取某一步骤的输入图 (业务核心：第N步的输入是第N-1步的输出，第1步的输入是原图)
    fun getInputImage(stepIndex: Int, projectSource: WorkImage?): WorkImage? {
        return if (stepIndex <= 0) projectSource else steps.getOrNull(stepIndex - 1)
    }

    // 3. 获取指定步骤之后的滤镜列表 (用于重算)
    fun getFiltersAfter(index: Int): List<ImageFilter> {
        return steps.drop(index).mapNotNull { it.appliedFilter }
    }

    // 4. 替换步骤链 (用于修改中间步骤后，拼接重算后的尾部)
    fun replaceSteps(startIndex: Int, newSuffix: List<WorkImage>): Pipeline {
        val prefix = steps.take(startIndex)
        val newSteps = prefix + newSuffix
        return copy(
            steps = newSteps,
            activeIndex = prefix.size + 1, // 焦点移动到被修改的那一步
            draft = EditSession() // 提交后重置草稿
        )
    }

    // 5. 追加新步骤 (用于应用新滤镜)
    fun appendStep(newImage: WorkImage): Pipeline {
        val newSteps = steps.take(activeIndex) + newImage
        return copy(
            steps = newSteps,
            activeIndex = newSteps.size,
            draft = EditSession()
        )
    }

    // 6. 开始编辑 (进入 Draft 状态)
    fun startEditing(filter: ImageFilter, projectSource: WorkImage?): Pipeline {
        val base = getInputImage(activeIndex, projectSource)
        return copy(
            draft = EditSession(
                activeFilter = filter,
                baseImage = base,
                previewImage = null
            )
        )
    }
}