package org.eu.freex.tools.modules.image.presentation.features.filter.components.renderers

import org.eu.freex.tools.modules.image.domain.model.BinarizationFilter
import org.eu.freex.tools.modules.image.domain.model.BlackWhiteInvertFilter
import org.eu.freex.tools.modules.image.domain.model.DenoiseFilter
import org.eu.freex.tools.modules.image.domain.model.DeskewFilter
import org.eu.freex.tools.modules.image.domain.model.ExtractBlobsFilter
import org.eu.freex.tools.modules.image.domain.model.ExtractContoursFilter
import org.eu.freex.tools.modules.image.domain.model.GrayscaleFilter
import org.eu.freex.tools.modules.image.domain.model.ImageFilter
import org.eu.freex.tools.modules.image.domain.model.MultiColorFilter
import org.eu.freex.tools.modules.image.domain.model.PosterizationFilter
import org.eu.freex.tools.modules.image.domain.model.RemoveLinesFilter
import org.eu.freex.tools.modules.image.domain.model.RemoveNoiseFilter
import org.eu.freex.tools.modules.image.domain.model.RotationFilter
import kotlin.reflect.KClass

object FilterUIRegistry {

    // 【核心改动 1】 Key 的类型改为 KClass<out AppFilter>
    // 这意味着我们通过“这个对象属于哪个类”来决定“使用哪个渲染器”
    private val renderers = mapOf<KClass<out ImageFilter>, FilterRenderer>(

        // 1. 有参数的滤镜：绑定专门的渲染器
        BinarizationFilter::class to BinarizationRenderer,
        // 【新增】注册多点找色
        MultiColorFilter::class to MultiColorRenderer,
        PosterizationFilter::class to PosterizationRenderer,
        GrayscaleFilter::class to GrayscaleRenderer,

        RemoveNoiseFilter::class to RemoveNoiseRenderer,
        RemoveLinesFilter::class to RemoveLinesRenderer,
        ExtractContoursFilter::class to ExtractContoursRenderer,
        ExtractBlobsFilter::class to ExtractBlobsRenderer,
        DeskewFilter::class to DeskewRenderer,
        RotationFilter::class to RotationRenderer,
        DenoiseFilter::class to EmptyRenderer,
        BlackWhiteInvertFilter::class to BlackWhiteInvertRenderer,
    )

    /**
     * 【核心改动 2】 根据传入实例的类型查找
     */
    fun getRenderer(filter: ImageFilter): FilterRenderer {
        // filter::class 拿到的是这个实例的具体类型（例如 BinarizationFilter）
        return renderers[filter::class] ?: EmptyRenderer
    }
}