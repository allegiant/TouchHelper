package org.eu.freex.tools.modules.image.domain.model

/**
 * 核心聚合根 (Aggregate Root)
 * 职责：
 * 1. 持有 Project 和 Pipeline
 * 2. 负责计算"当前显示什么" (业务逻辑下沉)
 * 3. 负责处理局部组件的更新 (Update 逻辑下沉)
 */
data class ImageWorkspace(
    val project: Project = Project(),
    val pipeline: Pipeline = Pipeline()
) {

    // ✅ 逻辑完美移入实体：这里是计算业务数据的最佳场所
    val activeDisplayImage: WorkImage?
        get() {
            // 1. 优先显示草稿
            pipeline.draft.previewImage?.let { return it }
            // 2. 其次显示流水线输出
            pipeline.activeOutputImage?.let { return it }
            // 3. 最后显示原图
            return project.activeImage?.copy(label = "原图")
        }

    // ✅ 逻辑完美移入实体
    val displayChain: List<WorkImage>
        get() = buildList {
            project.activeImage?.let { add(it.copy(label = "原图")) }
            addAll(pipeline.steps)
        }
}