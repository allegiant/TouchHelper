package org.eu.freex.tools.common


import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 全局窗口管理器
 * 用于在不同模块间控制主窗口的行为（如隐藏/显示）
 */
class AppWindowManager {
    private val _isAppVisible = MutableStateFlow(true)
    val isAppVisible = _isAppVisible.asStateFlow()

    fun hideWindow() {
        _isAppVisible.value = false
    }

    fun showWindow() {
        _isAppVisible.value = true
    }
}