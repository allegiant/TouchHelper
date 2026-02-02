package org.eu.freex.tools.modules.image.presentation.features.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.eu.freex.tools.common.utils.toHexString
import java.awt.image.BufferedImage

@Composable
fun DefaultHoverInfoOverlay(
    modifier: Modifier,
    pixelPos: IntOffset,
    inBounds: Boolean,
    image: BufferedImage
) {
    Box(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                MaterialTheme.shapes.small
            )
            .padding(8.dp)
    ) {
        // 获取颜色的逻辑移到这里
        val color = if (inBounds) try {
            Color(image.getRGB(pixelPos.x, pixelPos.y))
        } catch (e: Exception) {
            null
        } else null

        val colorHex = color?.toHexString(false) ?: "--"
        val text = if (inBounds) {
            "X: ${pixelPos.x}  Y: ${pixelPos.y}\nColor: $colorHex"
        } else {
            "Out of bounds"
        }

        Text(
            text = text,
            style = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        )
    }
}