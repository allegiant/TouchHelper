package org.eu.freex.tools.modules.image.presentation.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import org.eu.freex.tools.modules.image.application.WorkspaceUseCase
import org.eu.freex.tools.modules.image.domain.model.ImageWorkspace
import org.eu.freex.tools.modules.image.presentation.core.ImageUiState

/**
 * 定义 ViewModel 暴露给 Delegates 的上下文环境
 * 解耦 Delegate 与具体 ViewModel 的实现细节
 */
interface ViewModelContext {
    /**
     * 协程作用域，用于启动异步任务
     */
    val scope: CoroutineScope

    /**
     * 业务逻辑用例
     */
    val useCase: WorkspaceUseCase

    /**
     * UI 状态流 (只读)
     */
    val uiState: StateFlow<ImageUiState>

    /**
     * 原子性更新 Workspace 并自动刷新基础 UI 状态 (assets, pipeline, segmentation)。
     * * @param transform 带接收者的 Lambda，this 指向当前的 ImageWorkspace。
     * * 用法示例:
     * updateWorkspace { copy(assets = assets + newLayer) }
     */
    fun updateWorkspace(transform: ImageWorkspace.() -> ImageWorkspace)

    /**
     * 获取当前 Workspace 的快照。
     * * 注意：仅在确实需要读取当前数据且不立即更新时使用。
     * 如果目的是为了更新，请直接使用 updateWorkspace { ... } 以确保原子性。
     */
    fun getWorkspaceSnapshot(): ImageWorkspace

    /**
     * 更新 UI 临时状态 (如 loading, preview, cropper, pickingType 等)。
     * 这些状态通常不持久化到 Workspace 中。
     */
    fun updateUiState(transform: (ImageUiState) -> ImageUiState)
}