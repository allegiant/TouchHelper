
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