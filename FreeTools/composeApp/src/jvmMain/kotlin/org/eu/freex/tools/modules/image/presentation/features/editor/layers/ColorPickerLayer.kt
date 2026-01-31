/* Path: .../features/editor/layers/ColorPickerLayer.kt */
package org.eu.freex.tools.modules.image.presentation.features.editor.layers

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import org.eu.freex.tools.common.utils.ImageUtils
import org.eu.freex.tools.modules.image.presentation.viewmodel.PickingToolViewModel
import org.koin.compose.koinInject
import java.awt.image.BufferedImage
import kotlin.math.floor

@Composable
fun ColorPickerLayer(
    sourceImage: BufferedImage,
    viewModel: PickingToolViewModel = koinInject()
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val x = floor(offset.x).toInt()
                    val y = floor(offset.y).toInt()
                    // 边界检查
                    if (x in 0 until sourceImage.width && y in 0 until sourceImage.height) {
                        // 1. 耗时操作：获取颜色
                        val color = ImageUtils.getPixelColor(sourceImage, x, y)
                        // 2. 触发事件
                        viewModel.emitColorPick(x, y, color)
                    }
                }
            }
    )
}