package org.eu.freex.tools.modules.image.presentation.features.pipeline

import org.eu.freex.tools.modules.image.presentation.core.PipelineEvent

/**
 * 选中步骤
 * (Handler 将负责移动指针，并触发 startEditing 加载参数)
 */
data class SelectPipelineStep(val index: Int) : PipelineEvent

/**
 * 删除步骤
 * (Handler 将调用 UseCase 执行删除并重算)
 */
data class DeletePipelineStep(val index: Int) : PipelineEvent