package org.eu.freex.tools.common.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs

object ColorMatcher {

    /**
     * 判断两个颜色是否匹配
     * @param targetColor 目标颜色
     * @param testColor 待测颜色
     * @param offsetStr 偏色字符串 (例如 "101010" 或 "202020")
     */
    fun isMatch(targetColor: Color, testColor: Color, offsetStr: String): Boolean {
        val offset = parseOffset(offsetStr)

        val rDiff = abs((targetColor.red * 255).toInt() - (testColor.red * 255).toInt())
        val gDiff = abs((targetColor.green * 255).toInt() - (testColor.green * 255).toInt())
        val bDiff = abs((targetColor.blue * 255).toInt() - (testColor.blue * 255).toInt())

        return rDiff <= offset.r && gDiff <= offset.g && bDiff <= offset.b
    }

    private data class RGBOffset(val r: Int, val g: Int, val b: Int)

    private fun parseOffset(hex: String): RGBOffset {
        // 简单解析 "RRGGBB" 格式的偏色
        if (hex.length != 6) return RGBOffset(0, 0, 0)
        return try {
            val r = hex.substring(0, 2).toInt(16)
            val g = hex.substring(2, 4).toInt(16)
            val b = hex.substring(4, 6).toInt(16)
            RGBOffset(r, g, b)
        } catch (e: Exception) {
            RGBOffset(0, 0, 0)
        }
    }
}