
package org.eu.freex.tools.common.utils

import androidx.compose.ui.graphics.Color

/**
 * 将 Compose Color 转换为 Hex 字符串格式 (#RRGGBB)
 */
fun Color.toHexString(): String {
    // Compose Color value 是 ULong，转换为 Long 进行位运算
    val argb = this.value.toLong()
    val r = (argb shr 16 and 0xFF).toString(16).padStart(2, '0').uppercase()
    val g = (argb shr 8 and 0xFF).toString(16).padStart(2, '0').uppercase()
    val b = (argb and 0xFF).toString(16).padStart(2, '0').uppercase()
    return "#$r$g$b"
}

/**
 * 将 Hex 字符串 (#RRGGBB 或 #AARRGGBB) 解析为 Compose Color
 */
fun String.toComposeColor(): Color {
    if (this.isBlank()) return Color.Red
    return try {
        val cleanHex = this.removePrefix("#")
        val colorLong = when (cleanHex.length) {
            6 -> "FF$cleanHex".toLong(16) // 补全 Alpha 通道: RRGGBB -> FF RRGGBB
            8 -> cleanHex.toLong(16)      // AARRGGBB
            else -> 0xFFFF0000 // 格式错误返回红色
        }
        Color(colorLong)
    } catch (e: Exception) {
        Color.Red // 解析失败返回红色
    }
}