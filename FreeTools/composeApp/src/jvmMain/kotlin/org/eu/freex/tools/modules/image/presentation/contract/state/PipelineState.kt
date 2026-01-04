package org.eu.freex.tools.modules.image.presentation.contract.state

import org.eu.freex.tools.modules.image.domain.model.AppFilter
import org.eu.freex.tools.modules.image.domain.model.ViewFilter
import org.eu.freex.tools.modules.image.domain.model.WorkImage

// --- 【核心重构】编辑草稿状态 ---
// 这个状态专门用于持有当前正在调整但尚未“应用”或“保存”的参数和预览图
data class DraftState(
    val activeFilter: AppFilter = ViewFilter, // 当前属性面板应该显示的滤镜参数
    val previewImage: WorkImage? = null,      // 经过该滤镜处理后的预览图 (用于画布显示)
    val baseImage: WorkImage? = null          // 该滤镜是基于哪张图处理的 (用于参数变化时重新计算)
)

// --- 流水线状态 ---
data class PipelineState(
    val pipelineSteps: List<WorkImage> = emptyList(), // 已提交的步骤列表
    val selectedPipelineIndex: Int = 0,               // 0 代表原图，1..N 代表步骤
    val draft: DraftState = DraftState()              // 当前的编辑区域状态
)