package org.eu.freex.tools.modules.image.presentation.features.filter

import org.eu.freex.tools.modules.image.domain.model.ImageFilter
import org.eu.freex.tools.modules.image.presentation.core.FilterEvent

/**
 * 预览滤镜
 * @param forceReloadBaseImage 是否强制重新获取输入图（用于撤销重做或重置场景）
 */
data class PreviewFilter(
    val filter: ImageFilter,
    val forceReloadBaseImage: Boolean = false
) : FilterEvent

/**
 * 确认更新当前步骤 (修改已存在的步骤)
 */
object UpdateCurrentStep : FilterEvent

/**
 * 应用为新步骤 (追加到队尾)
 */
object ApplyNewStep : FilterEvent