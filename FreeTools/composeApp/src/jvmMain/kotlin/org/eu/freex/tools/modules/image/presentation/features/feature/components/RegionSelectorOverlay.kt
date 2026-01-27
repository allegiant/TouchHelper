package org.eu.freex.tools.modules.image.presentation.features.feature.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntRect // [修改] 使用官方标准类

@Composable
fun RegionSelectorOverlay(
    modifier: Modifier = Modifier, // [新增] 接收外部 Modifier
    onRegionSelected: (IntRect) -> Unit,
    onCancel: () -> Unit
) {
    var startPoint by remember { mutableStateOf<Offset?>(null) }
    var currentPoint by remember { mutableStateOf<Offset?>(null) }

    Canvas(
        modifier = modifier // [修改] 应用传入的 modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        startPoint = offset
                        currentPoint = offset
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        currentPoint = change.position
                    },
                    onDragEnd = {
                        val start = startPoint
                        val end = currentPoint
                        if (start != null && end != null) {
                            // [逻辑适配] 计算左上角和右下角
                            val left = minOf(start.x, end.x).toInt()
                            val top = minOf(start.y, end.y).toInt()
                            val right = maxOf(start.x, end.x).toInt()
                            val bottom = maxOf(start.y, end.y).toInt()

                            // [注意] 官方 IntRect 构造函数是 (left, top, right, bottom)
                            val rect = IntRect(left, top, right, bottom)

                            // 只有当区域足够大时才触发
                            if (rect.width > 5 && rect.height > 5) {
                                onRegionSelected(rect)
                            } else {
                                onCancel()
                            }
                        }
                        startPoint = null
                        currentPoint = null
                    },
                    onDragCancel = {
                        startPoint = null
                        currentPoint = null
                        onCancel()
                    }
                )
            }
    ) {
        // 绘制半透明背景和选框
        drawRect(Color.Black.copy(alpha = 0.3f))

        val start = startPoint
        val end = currentPoint
        if (start != null && end != null) {
            val topLeft = Offset(minOf(start.x, end.x), minOf(start.y, end.y))
            val size = Size(kotlin.math.abs(end.x - start.x), kotlin.math.abs(end.y - start.y))

            // 挖空选中区域 (使用 BlendMode.Clear 或绘制四个矩形，这里假设您已有实现)
            drawRect(Color.Transparent, topLeft, size, style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))))
            drawRect(Color.White, topLeft, size, style = Stroke(1f))
        }
    }
}