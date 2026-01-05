package org.eu.freex.tools.common.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * MVI ViewModel 基类
 * @param E 事件类型
 * @param S 状态类型
 */
abstract class BaseViewModel<E, S>(
    initialState: S,
    private val dispatcher: BaseEventDispatcher<E, S>
) : ViewModel() {

    // 1. 标准 State 管理
    // protected 允许子类（如 observeProjectChanges）直接修改状态
    protected val _uiState = MutableStateFlow(initialState)
    val state: S get() = _uiState.value
    val uiState = _uiState.asStateFlow()

    // 2. 标准 Effect (副作用/Toast) 管理
    private val _uiEffect = Channel<String>(Channel.BUFFERED)
    val uiEffect = _uiEffect.receiveAsFlow()

    // 发送 Toast 的帮助方法
    protected fun sendEffect(msg: String) {
        viewModelScope.launch { _uiEffect.send(msg) }
    }

    // 3. 统一的事件处理入口
    fun handleEvent(event: E) {
        viewModelScope.launch {
            // A. 自动开启 Loading (如果 State 支持)
            trySetLoading(true)

            // B. 准备 Toast 回调给 Handler 使用
            val toast: (String) -> Unit = { msg -> sendEffect(msg) }

            // C. 自动分发事件
            // dispatcher.dispatch 会遍历所有 handler 找到能处理的那个
            val newState = dispatcher.dispatch(event, state, toast)

            // D. 更新状态
            _uiState.update { newState }

            // E. 自动关闭 Loading
            trySetLoading(false)
        }
    }

    // 辅助方法：尝试设置 Loading
    private fun trySetLoading(isLoading: Boolean) {
        val currentState = _uiState.value
        if (currentState is LoadingAware<*>) {
            @Suppress("UNCHECKED_CAST")
            val loadingState = currentState as LoadingAware<S>
            _uiState.update { loadingState.updateLoading(isLoading) }
        }
    }
}