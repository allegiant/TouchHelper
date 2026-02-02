package org.eu.freex.tools.modules.image.presentation.features.editor.components.foundation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.eu.freex.tools.common.utils.toHexString
import java.awt.image.BufferedImage

/**
 * [PixelInfoBoard]
 * 简单的像素信息看板。通常固定在画布角落。
 */
@Composable
fun PixelInfoBoard(
    modifier: Modifier = Modifier,
    pixelPos: IntOffset,
    image: BufferedImage?
) {
    if (image == null) return

    val inBounds = pixelPos.x in 0 until image.width && pixelPos.y in 0 until image.height

    Box(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                MaterialTheme.shapes.small
            )
            .padding(8.dp)
    ) {
        // 获取颜色
        val colorHex = if (inBounds) {
            try {
                val rgb = image.getRGB(pixelPos.x, pixelPos.y)
                Color(rgb).toHexString(false)
            } catch (e: Exception) { "--" }
        } else "--"

        val text = if (inBounds) {
            "X: ${pixelPos.x}  Y: ${pixelPos.y}\nColor: $colorHex"
        } else {
            "X: ${pixelPos.x}  Y: ${pixelPos.y}\n(Out of bounds)"
        }

        Text(
            text = text,
            style = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        )
    }
}