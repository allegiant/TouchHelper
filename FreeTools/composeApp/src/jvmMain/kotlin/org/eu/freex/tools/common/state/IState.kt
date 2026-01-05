package org.eu.freex.tools.common.state

/**
 * 可选接口：如果你的 State 实现了这个接口，BaseViewModel 会自动处理 Loading
 */
interface LoadingAware<S> {
    fun updateLoading(isLoading: Boolean): S
}