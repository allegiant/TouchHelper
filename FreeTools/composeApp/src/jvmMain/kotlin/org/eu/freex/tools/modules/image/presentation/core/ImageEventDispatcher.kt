package org.eu.freex.tools.modules.image.presentation.core

import org.eu.freex.tools.common.state.BaseEventDispatcher


/**
 * Image 模块的具体分发器
 * 逻辑全部委托给 BaseEventDispatcher，这里只负责类型绑定
 */
class ImageEventDispatcher(
    handlers: List<ImageEventHandler>
) : BaseEventDispatcher<ImageUiEvent, ImageUiState>(handlers)