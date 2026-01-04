package org.eu.freex.tools.modules.image.domain.model

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import uniffi.touch_core.applyBinarization
import uniffi.touch_core.applyBlackwhiteInvert
import uniffi.touch_core.applyColorInvert
import uniffi.touch_core.applyDenoise
import uniffi.touch_core.applyGrayscale
import kotlin.math.roundToInt
import uniffi.touch_core.BinarizationFilter as RustBinarizationFilter
import uniffi.touch_core.BlackWhiteInvertFilter as RustBlackWhiteInvertFilter
import uniffi.touch_core.ColorInvertFilter as RustColorInvertFilter
import uniffi.touch_core.DenoiseFilter as RustDenoiseFilter
import uniffi.touch_core.GrayscaleFilter as RustGrayscaleFilter


/**
 * 滤镜能力的顶层抽象
 */
@Serializable
sealed interface AppFilter {
    val name: String // 用于 UI 显示

    // 核心逻辑：执行滤镜
    fun apply(pixels: ByteArray, w: Int, h: Int): ByteArray
}

@OptIn(InternalSerializationApi::class)
val AppFilter.type: String
    get() = this::class.serializer().descriptor.serialName

/**
 * 4. 查看模式 (无滤镜)
 * 这是一个占位符，apply 方法直接返回原图
 */
@Serializable
@SerialName("ORIGIN")
object ViewFilter : AppFilter {
    override val name = "原图"
    override fun apply(pixels: ByteArray, w: Int, h: Int): ByteArray = pixels
}

/**
 * 1. 二值化滤镜
 * 直接持有 UI 状态 (Range, Boolean)，内部负责转为 Rust 参数
 */
@Serializable
@SerialName("BINARIZATION")
data class BinarizationFilter(
    val min: Float = 0f,
    val max: Float = 72f,
    val isRgbAvg: Boolean = true
) : AppFilter {
    override val name = "二值化"

    override fun apply(pixels: ByteArray, w: Int, h: Int): ByteArray {
        // 构造 Rust 参数
        val min = min.roundToInt().coerceIn(0, 255)
        val max = max.roundToInt().coerceIn(0, 255)
        val rustOptions = RustBinarizationFilter(
            thresholdMin = min,
            thresholdMax = max,
            isRgbAvg = isRgbAvg
        )
        // 调用具体的 Rust 函数
        return applyBinarization(pixels, w, h, rustOptions)
    }
}

/**
 * 2. 灰度滤镜
 */
@Serializable
@SerialName("GRAYSCALE")
object GrayscaleFilter : AppFilter {
    override val name = "灰度化"
    override fun apply(pixels: ByteArray, w: Int, h: Int): ByteArray {
        val rustOptions = RustGrayscaleFilter()
        return applyGrayscale(pixels, w, h, rustOptions)
    }
}

/**
 * 3. 反色滤镜
 */
@Serializable
@SerialName("COLOR_INVERT")
object ColorInvertFilter : AppFilter {
    override val name = "颜色反转"
    override fun apply(pixels: ByteArray, w: Int, h: Int): ByteArray {
        val rustOptions = RustColorInvertFilter()
        return applyColorInvert(pixels, w, h, rustOptions)
    }
}

/**
 * 2. 去噪滤镜
 */
@Serializable
@SerialName("DENOISE")
data class DenoiseFilter(
    val radius: UInt = 1u,
) : AppFilter {
    override val name = "去噪"
    override fun apply(pixels: ByteArray, w: Int, h: Int): ByteArray {
        val rustOptions = RustDenoiseFilter(radius)
        return applyDenoise(pixels, w, h, rustOptions)
    }
}

/**
 * 黑白反转
 */
@Serializable
@SerialName("BLACK_WHITE_INVERT")
object BlackWhiteInvertFilter : AppFilter {
    override val name = "黑白反转"
    override fun apply(pixels: ByteArray, w: Int, h: Int): ByteArray {
        val rustOptions = RustBlackWhiteInvertFilter()
        return applyBlackwhiteInvert(pixels, w, h, rustOptions)
    }
}