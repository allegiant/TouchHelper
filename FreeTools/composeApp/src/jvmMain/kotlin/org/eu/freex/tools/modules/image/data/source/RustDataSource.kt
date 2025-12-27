package org.eu.freex.tools.modules.image.data.source

import org.eu.freex.tools.model.*
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
// 引入 uniffi 生成的 Rust 绑定类
import uniffi.touch_core.ImageFilter as RustFilter
import uniffi.touch_core.ColorFilterType as RustColorType
import uniffi.touch_core.BlackWhiteFilterType as RustBWType
import uniffi.touch_core.CommonFilterType as RustCommonType

class RustDataSource {

    /**
     * 调用 Rust 核心应用滤镜
     */
    fun applyFilter(image: BufferedImage, filter: ImageFilter, p1: Int? = null, p2: Int? = null): BufferedImage {
        // 1. 映射 Kotlin 枚举 -> Rust 枚举
        val rustFilter = mapToRustFilter(filter) ?: run {
            println("RustDataSource: Warning - No mapping found for filter ${filter.label}")
            return image
        }

        return try {
            // 2. 图片 -> PNG 字节流 (IO操作)
            val inputBytes = bufferedImageToBytes(image)

            // 3. 调用 Rust 核心函数
            val outputBytes = uniffi.touch_core.applyFilter(inputBytes, rustFilter, p1, p2)

            // 4. PNG 字节流 -> 图片
            bytesToBufferedImage(outputBytes)
        } catch (e: Exception) {
            println("RustDataSource: Error applying filter - ${e.message}")
            e.printStackTrace()
            image // 发生错误时返回原图，避免崩溃
        }
    }

    /**
     * 调用 Rust 核心扫描连通区域
     */
    fun scanComponents(image: BufferedImage, rules: List<ColorRule>): List<androidx.compose.ui.geometry.Rect> {
        return try {
            val inputBytes = bufferedImageToBytes(image)

            // 映射规则模型
            val rustRules = rules.map {
                uniffi.touch_core.ColorRule(it.id, it.targetHex, it.biasHex, it.isEnabled)
            }

            // 调用 Rust
            val rustRects = uniffi.touch_core.scanComponents(inputBytes, rustRules)

            // 映射结果 Rect -> Compose Rect
            rustRects.map { r ->
                androidx.compose.ui.geometry.Rect(
                    left = r.left.toFloat(),
                    top = r.top.toFloat(),
                    right = r.left.toFloat() + r.width.toFloat(),
                    bottom = r.top.toFloat() + r.height.toFloat(),
                )
            }
        } catch (e: Exception) {
            println("RustDataSource: Error scanning components - ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    // --- 私有辅助方法 ---

    private fun mapToRustFilter(filter: ImageFilter): RustFilter? {
        return when (filter) {
            // ==========================
            // 1. 彩色处理 (ColorFilterType)
            // ==========================
            ColorFilterType.BINARIZATION -> RustFilter.Color(RustColorType.BINARIZATION)
            ColorFilterType.GRAYSCALE -> RustFilter.Color(RustColorType.GRAYSCALE)
            ColorFilterType.COLOR_PICK -> RustFilter.Color(RustColorType.COLOR_PICK)
            ColorFilterType.POSTERIZE -> RustFilter.Color(RustColorType.POSTERIZE)

            // ==========================
            // 2. 黑白处理 (BlackWhiteFilterType)
            // ==========================
            BlackWhiteFilterType.DENOISE -> RustFilter.BlackWhite(RustBWType.DENOISE)
            BlackWhiteFilterType.REMOVE_LINES -> RustFilter.BlackWhite(RustBWType.REMOVE_LINES)
            BlackWhiteFilterType.CONTOURS -> RustFilter.BlackWhite(RustBWType.CONTOURS)
            BlackWhiteFilterType.EXTRACT_BLOBS -> RustFilter.BlackWhite(RustBWType.EXTRACT_BLOBS)
            BlackWhiteFilterType.DESKEW -> RustFilter.BlackWhite(RustBWType.DESKEW)
            BlackWhiteFilterType.ROTATE_CORRECT -> RustFilter.BlackWhite(RustBWType.ROTATE_CORRECT)

            // 【修复】INVERT 应该在这里
            BlackWhiteFilterType.INVERT -> RustFilter.BlackWhite(RustBWType.INVERT)

            BlackWhiteFilterType.DILATE_ERODE -> RustFilter.BlackWhite(RustBWType.DILATE_ERODE)
            BlackWhiteFilterType.SKELETON -> RustFilter.BlackWhite(RustBWType.SKELETON)
            BlackWhiteFilterType.FENCE_ADJUST -> RustFilter.BlackWhite(RustBWType.FENCE_ADJUST)
            BlackWhiteFilterType.VALID_IMAGE -> RustFilter.BlackWhite(RustBWType.VALID_IMAGE)
            BlackWhiteFilterType.KEEP_SIZE -> RustFilter.BlackWhite(RustBWType.KEEP_SIZE)

            // ==========================
            // 3. 通用处理 (CommonFilterType)
            // ==========================
            CommonFilterType.SCALE_RATIO -> RustFilter.Common(RustCommonType.SCALE_RATIO)
            CommonFilterType.SCALE_NORM -> RustFilter.Common(RustCommonType.SCALE_NORM)
            CommonFilterType.FIXED_ROTATE -> RustFilter.Common(RustCommonType.FIXED_ROTATE)
            CommonFilterType.EXTEND_CROP -> RustFilter.Common(RustCommonType.EXTEND_CROP)
            CommonFilterType.FIXED_SMOOTH -> RustFilter.Common(RustCommonType.FIXED_SMOOTH)
            CommonFilterType.MEDIAN_BLUR -> RustFilter.Common(RustCommonType.MEDIAN_BLUR)

            // 默认情况 (ViewFilter 或未知类型)
            else -> null
        }
    }

    private fun bufferedImageToBytes(image: BufferedImage): ByteArray {
        val stream = ByteArrayOutputStream()
        ImageIO.write(image, "png", stream)
        return stream.toByteArray()
    }

    private fun bytesToBufferedImage(bytes: ByteArray): BufferedImage {
        return ImageIO.read(ByteArrayInputStream(bytes))
    }
}