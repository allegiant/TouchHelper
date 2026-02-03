/* Path: .../features/editor/layers/PointPickerLayer.kt */
package org.eu.freex.tools.modules.image.presentation.features.editor.layers

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import kotlin.math.floor

@Composable
fun PointPickerLayer(
    imageSize: IntSize,
    onPick: (Int, Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val x = floor(offset.x).toInt()
                    val y = floor(offset.y).toInt()
                    // 边界检查
                    if (x in 0 until imageSize.width && y in 0 until imageSize.height) {
                        // 触发事件 (无需颜色)
                        onPick(x, y)
                    }
                }
            }
    )
}