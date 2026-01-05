package org.eu.freex.tools.modules.image.presentation.core


/**
 * 事件分发器
 * 职责：持有所有已注册的 Handler，并将 Event 路由给正确的 Handler
 */
class EventDispatcher(
    // 自动注入所有实现了 ImageEventHandler 的实例
    private val handlers: List<ImageEventHandler>
) {
    suspend fun dispatch(
        event: ImageUiEvent,
        currentState: ImageUiState,
        showToast: (String) -> Unit
    ): ImageUiState {
        // 遍历所有 Handler
        for (handler in handlers) {
            // 尝试让 Handler 处理
            val result = handler.handle(event, currentState, showToast)

            // 如果 Handler 返回了非空结果，说明它认领并处理了这个事件
            if (result != null) {
                return result
            }
        }

        // 如果没有任何 Handler 处理该事件，原样返回状态（或者打个日志）
        println("Warning: No handler found for event: $event")
        return currentState
    }
}