
package org.eu.freex.tools.common.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb


/**
 * 将 Compose Color 转换为 Hex 字符串格式 (#RRGGBB)
 */
fun Color.toHexString(bool: Boolean): String {
    // [关键修复] 使用 toArgb() 获取标准的 ARGB 整数，而不是直接操作 value (ULong)
    // 直接操作 value 会导致颜色空间位移错误，从而一直是 0 (黑色)
    val argb = this.toArgb()

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
            6 -> "FF$cleanHex".toLong(16) // 补全 Alpha 通道
            8 -> cleanHex.toLong(16)
            else -> 0xFFFF0000
        }
        Color(colorLong)
    } catch (e: Exception) {
        Color.Red
    }
}