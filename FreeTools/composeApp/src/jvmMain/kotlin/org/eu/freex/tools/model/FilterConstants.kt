package org.eu.freex.tools.model

/**
 * 滤镜参数常量定义
 * 避免在代码中散落 "min", "max" 等魔法字符串
 */
object FilterConstants {
    // 参数键名
    const val PARAM_MIN = "min"
    const val PARAM_MAX = "max"
    const val PARAM_RGB_AVG = "rgbAvg"

    // 可以在这里扩展其他滤镜的参数 Key
    // const val PARAM_BLUR_RADIUS = "radius"
}