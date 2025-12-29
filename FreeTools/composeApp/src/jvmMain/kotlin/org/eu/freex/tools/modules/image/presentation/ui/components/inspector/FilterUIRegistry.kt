package org.eu.freex.tools.modules.image.presentation.ui.components.inspector

import org.eu.freex.tools.model.label
import org.eu.freex.tools.modules.image.presentation.ui.components.inspector.core.FilterRenderer
import org.eu.freex.tools.modules.image.presentation.ui.components.inspector.impl.BinarizationRenderer
import org.eu.freex.tools.modules.image.presentation.ui.components.inspector.impl.EmptyRenderer
import uniffi.touch_core.ColorFilterType
import uniffi.touch_core.ImageFilter

object FilterUIRegistry {
    // 核心映射表：Key 是滤镜类型的唯一标识，Value 是渲染器
    // 提示：如果 ImageFilter 是 data class，可以直接作为 Key，
    // 但为了性能和匹配逻辑，通常推荐使用枚举或特征字符串作为 Key。

    private val renderers = mapOf<String, FilterRenderer>(
        // 绑定二值化 -> 二值化渲染器
        ImageFilter.Color(ColorFilterType.BINARIZATION).label to BinarizationRenderer,

        // 绑定灰度 -> 灰度渲染器 (假设您实现了 GrayscaleRenderer)
        // ImageFilter.Color(ColorFilterType.GRAYSCALE).label to GrayscaleRenderer,
    )

    /**
     * 对外提供查找方法
     * 只有这里可能残留一点点逻辑，但主界面完全不知情
     */
    fun getRenderer(filter: ImageFilter): FilterRenderer {
        // 直接查表，查不到就返回空渲染器
        return renderers[filter.label] ?: EmptyRenderer
    }
}