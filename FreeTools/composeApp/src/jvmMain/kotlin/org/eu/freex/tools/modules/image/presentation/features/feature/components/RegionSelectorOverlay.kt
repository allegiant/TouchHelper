package org.eu.freex.tools.modules.image.presentation.features.feature.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.modules.image.presentation.viewmodel.IntRect
import kotlin.math.abs
import kotlin.math.min

// [新增] 区域框选交互组件
@Composable
fun RegionSelectorOverlay(
    onRegionSelected: (IntRect) -> Unit,
    onCancel: () -> Unit
) {
    var startOffset by remember { mutableStateOf<Offset?>(null) }
    var currentOffset by remember { mutableStateOf<Offset?>(null) }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            // 拦截点击，变为手势处理
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { startOffset = it },
                    onDrag = { change, _ ->
                        change.consume()
                        currentOffset = change.position
                    },
                    onDragEnd = {
                        if (startOffset != null && currentOffset != null) {
                            val x = min(startOffset!!.x, currentOffset!!.x).toInt()
                            val y = min(startOffset!!.y, currentOffset!!.y).toInt()
                            val w = abs(startOffset!!.x - currentOffset!!.x).toInt()
                            val h = abs(startOffset!!.y - currentOffset!!.y).toInt()

                            // 防止误触，微小移动不算框选
                            if (w > 5 && h > 5) {
                                onRegionSelected(IntRect(x, y, w, h))
                            }
                        }
                        startOffset = null
                        currentOffset = null
                    },
                    onDragCancel = { onCancel() }
                )
            }
    ) {
        // 1. 绘制半透明黑色遮罩
        drawRect(Color.Black.copy(alpha = 0.3f))

        // 2. 绘制高亮选区 (挖空+描边)
        if (startOffset != null && currentOffset != null) {
            val topLeft = Offset(
                min(startOffset!!.x, currentOffset!!.x),
                min(startOffset!!.y, currentOffset!!.y)
            )
            val size = Size(
                abs(startOffset!!.x - currentOffset!!.x),
                abs(startOffset!!.y - currentOffset!!.y)
            )

            // 挖空 (Clear 模式)
            drawRect(Color.Transparent, topLeft = topLeft, size = size, blendMode = BlendMode.Clear)
            // 红框描边
            drawRect(Color.Red, topLeft = topLeft, size = size, style = Stroke(2.dp.toPx()))
        }
    }
}