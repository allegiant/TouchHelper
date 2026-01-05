package org.eu.freex.tools.common.state

/**
 * 通用的事件处理器接口
 * @param E 事件类型 (如 ImageUiEvent)
 * @param S 状态类型 (如 ImageUiState)
 */
interface IEventHandler<E, S> {
    /**
     * @return 返回新的 State 表示处理了事件；返回 null 表示不处理。
     */
    suspend fun handle(event: E, state: S, showToast: (String) -> Unit): S?
}

/**
 * 通用的事件分发器基类 (Base Dispatcher)
 * 核心逻辑：遍历 handlers，找到第一个能处理该事件的 handler。
 */
open class BaseEventDispatcher<E, S>(val handlers: List<IEventHandler<E, S>>) {
    suspend fun dispatch(
        event: E,
        currentState: S,
        showToast: (String) -> Unit
    ): S {
        for (handler in handlers) {
            // 尝试让 Handler 处理
            val result = handler.handle(event, currentState, showToast)
            if (result != null) {
                return result
            }
        }

        // 如果没有 handler 处理，打印日志并返回原状态
        println("Warning: No handler found for event: $event")
        return currentState
    }
}