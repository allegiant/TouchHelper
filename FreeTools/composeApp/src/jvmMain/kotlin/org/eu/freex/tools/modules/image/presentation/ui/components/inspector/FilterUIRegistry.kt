package org.eu.freex.tools.modules.image.presentation.ui.components.inspector

import org.eu.freex.tools.model.* // 引入 AppFilter 及其所有子类
import org.eu.freex.tools.modules.image.presentation.ui.components.inspector.core.FilterRenderer
import org.eu.freex.tools.modules.image.presentation.ui.components.inspector.impl.BinarizationRenderer
import org.eu.freex.tools.modules.image.presentation.ui.components.inspector.impl.EmptyRenderer
import kotlin.reflect.KClass // 必须引入这个

object FilterUIRegistry {

    // 【核心改动 1】 Key 的类型改为 KClass<out AppFilter>
    // 这意味着我们通过“这个对象属于哪个类”来决定“使用哪个渲染器”
    private val renderers = mapOf<KClass<out AppFilter>, FilterRenderer>(

        // 1. 有参数的滤镜：绑定专门的渲染器
        BinarizationFilter::class to BinarizationRenderer,
        DenoiseFilter::class to EmptyRenderer, // 如果你有 DenoiseRenderer 就换成它，没有就用 Empty

        // 2. 无参数的滤镜 (object)：通常不需要额外的 UI 面板，直接绑定 EmptyRenderer
        // 也可以不写，依赖 getRenderer 的默认值，但显式写出来更清晰
        ViewFilter::class to EmptyRenderer,
        GrayscaleFilter::class to EmptyRenderer,
        ColorInvertFilter::class to EmptyRenderer,
        BlackWhiteInvertFilter::class to EmptyRenderer
    )

    /**
     * 【核心改动 2】 根据传入实例的类型查找
     */
    fun getRenderer(filter: AppFilter): FilterRenderer {
        // filter::class 拿到的是这个实例的具体类型（例如 BinarizationFilter）
        return renderers[filter::class] ?: EmptyRenderer
    }
}