package org.eu.freex.tools.modules.image.presentation.core

/**
 * UI 事件基类 (Command 模式)
 */
interface ImageUiEvent {
    fun ImageActionScope.execute()
}