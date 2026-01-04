package org.eu.freex.tools.modules.image.presentation.core

import androidx.compose.runtime.staticCompositionLocalOf
import org.eu.freex.tools.modules.image.presentation.viewmodel.ImageViewModel

/**
 * 全局 ViewModel 提供者
 * 使用 staticCompositionLocalOf 是因为 ViewModel 实例在整个生命周期中通常不会改变。
 * 如果它意外改变，我们希望整个 UI 树重组以确保状态一致。
 */
val LocalImageViewModel = staticCompositionLocalOf<ImageViewModel> {
    error("LocalImageViewModel 未提供! 请在 ImageWorkbench 或上层组件中使用 CompositionLocalProvider 提供它。")
}