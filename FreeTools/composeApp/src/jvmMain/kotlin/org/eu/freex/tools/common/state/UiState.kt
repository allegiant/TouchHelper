package org.eu.freex.tools.common.state

/**
 * 通用的 UI 状态接口 (Generic UI State)
 * @param S 具体的 State 类型 (如 ImageUiState, TextUiState)
 * @param C 具体的组件密封接口 (如 ImageModel, TextModel)
 */
interface UiState<S, C> {
    // 核心契约：状态必须知道如何更新它的组件
    fun update(model: C): S
}

/**
 * 核心魔法：通用的中缀扩展函数
 * 适用于任何实现了 UiState 的类。
 * * 用法：
 * - pipeline.activateStep(...) into state
 * - textLayer.updateContent(...) into textState
 */
infix fun <S, C> C.into(state: UiState<S, C>): S {
    return state.update(this)
}