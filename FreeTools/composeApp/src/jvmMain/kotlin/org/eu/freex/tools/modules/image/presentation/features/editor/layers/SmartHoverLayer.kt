/* Path: .../editor/layers/SmartHoverLayer.kt */
package org.eu.freex.tools.modules.image.presentation.features.editor.layers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.modules.image.presentation.features.editor.components.foundation.MagnifierOverlay
import org.eu.freex.tools.modules.image.presentation.features.editor.components.foundation.PixelInfoBoard
import org.eu.freex.tools.modules.image.presentation.viewmodel.EditorCanvasTransform
import java.awt.image.BufferedImage
import kotlin.math.floor

@Composable
fun SmartHoverLayer(
    sourceImage: BufferedImage,
    containerSize: IntSize,
    transformState: EditorCanvasTransform,
    hoverPixelPos: Offset?,
    showMagnifier: Boolean // [修改] 直接由外部决定是否显示放大镜
) {
    if (hoverPixelPos == null) return

    Box(modifier = Modifier.fillMaxSize()) {
        val pixelX = floor(hoverPixelPos.x).toInt()
        val pixelY = floor(hoverPixelPos.y).toInt()
        val pixelPos = IntOffset(pixelX, pixelY)
        val inBounds = pixelX in 0 until sourceImage.width && pixelY in 0 until sourceImage.height

        // 逻辑简化：只要外部说显示，且坐标在图内，就显示
        if (showMagnifier && inBounds) {
            val scale = transformState.scale
            val pan = transformState.pan

            val screenX = (containerSize.width / 2f) + pan.x + (hoverPixelPos.x - sourceImage.width / 2f) * scale
            val screenY = (containerSize.height / 2f) + pan.y + (hoverPixelPos.y - sourceImage.height / 2f) * scale

            MagnifierOverlay(
                sourceImage = sourceImage,
                centerPixel = pixelPos,
                screenPos = Offset(screenX, screenY)
            )
        } else {
            // 否则显示普通信息板
            PixelInfoBoard(
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                pixelPos = pixelPos,
                image = sourceImage
            )
        }
    }
}