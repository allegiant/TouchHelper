package org.eu.freex.tools.modules.image.presentation.core

import org.eu.freex.tools.common.state.IEventHandler


/**
 * 所有业务模块 Handler 的基类
 * 实现了"自动注册"的基础契约
 */
interface ImageEventHandler: IEventHandler<ImageUiEvent, ImageUiState> {
    /**
     * 尝试处理事件
     * @param event UI 发出的事件
     * @param state 当前 UI 状态
     * @param showToast 发送 Toast 的回调
     * @return 如果该 Handler 处理了这个事件，返回新的 State；如果该事件不属于此 Handler 负责，返回 null。
     */
    override suspend fun handle(
        event: ImageUiEvent,
        state: ImageUiState,
        showToast: (String) -> Unit
    ): ImageUiState?
}