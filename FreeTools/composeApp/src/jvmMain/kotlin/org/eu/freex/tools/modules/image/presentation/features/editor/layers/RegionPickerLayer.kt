package org.eu.freex.tools.modules.image.presentation.features.editor.layers

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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import org.eu.freex.tools.modules.image.presentation.viewmodel.PickingToolViewModel
import org.koin.compose.koinInject
import java.awt.image.BufferedImage
import kotlin.math.max
import kotlin.math.min

/**
 * [RegionPickerLayer]
 * 区域选择图层 (框选工具)
 *
 * 功能：
 * 1. 拦截手势，允许用户在画面上拖拽出一个矩形框。
 * 2. 拖拽结束时，计算相对于原图的坐标。
 * 3. 裁剪出目标区域并传递给 [PickingToolViewModel] 用于二值化预览。
 * 4. 自动退出工具模式 (可选)。
 */
@Composable
fun RegionPickerLayer(
    sourceImage: BufferedImage,
    onCrop: (BufferedImage) -> Unit
) {
    // 记录拖拽状态
    var startOffset by remember { mutableStateOf<Offset?>(null) }
    var currentOffset by remember { mutableStateOf<Offset?>(null) }
    // 记录最终确定的矩形 (用于持续显示，直到下一次操作)
    var selectionRect by remember { mutableStateOf<Rect?>(null) }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        startOffset = offset
                        currentOffset = offset
                        selectionRect = null // 开始新拖拽时，清除旧框
                    },
                    onDrag = { change: PointerInputChange, dragAmount: Offset ->
                        change.consume()
                        currentOffset = currentOffset?.plus(dragAmount)
                    },
                    onDragEnd = {
                        val start = startOffset
                        val end = currentOffset

                        if (start != null && end != null) {
                            // 1. 计算规范化的矩形 (处理从右下往左上拖拽的情况)
                            val rect = calculateClampedRect(
                                start,
                                end,
                                sourceImage.width,
                                sourceImage.height
                            )

                            selectionRect = rect

                            // 2. 执行裁剪逻辑
                            if (rect.width > 1 && rect.height > 1) { // 忽略太小的误触
                                try {
                                    val crop = sourceImage.getSubimage(
                                        rect.left.toInt(),
                                        rect.top.toInt(),
                                        rect.width.toInt(),
                                        rect.height.toInt()
                                    )
                                    // 3. 提交给 VM
                                    onCrop(crop)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    },
                    onDragCancel = {
                        startOffset = null
                        currentOffset = null
                    }
                )
            }
    ) {
        // --- 绘制逻辑 ---

        // A. 正在拖拽中的动态框
        val start = startOffset
        val current = currentOffset
        if (start != null && current != null) {
            val dynamicRect = calculateClampedRect(start, current, sourceImage.width, sourceImage.height)
            drawSelectionBox(dynamicRect)
        }
        // B. 已经确定的静态框 (回显)
        else if (selectionRect != null) {
            drawSelectionBox(selectionRect!!)
        }
    }
}

/**
 * 绘制统一风格的选框
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSelectionBox(rect: Rect) {
    // 1. 半透明填充 (让用户知道这就选中的区域)
    drawRect(
        color = Color(0xFF2196F3).copy(alpha = 0.2f),
        topLeft = rect.topLeft,
        size = rect.size
    )

    // 2. 蚂蚁线边框 (黑白相间，保证在任何背景色上都可见)
    // 第一层：白色虚线
    drawRect(
        color = Color.White,
        topLeft = rect.topLeft,
        size = rect.size,
        style = Stroke(
            width = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
        )
    )

    // 第二层：蓝色实线边框 (可选，增强边界感)
    drawRect(
        color = Color(0xFF2196F3),
        topLeft = rect.topLeft,
        size = rect.size,
        style = Stroke(width = 1f)
    )

    // 3. 四角手柄 (可选，增强专业感)
    val handleSize = 6f
    drawCircle(Color.White, radius = handleSize, center = rect.topLeft)
    drawCircle(Color.White, radius = handleSize, center = rect.bottomRight)
    drawCircle(Color.White, radius = handleSize, center = rect.topRight)
    drawCircle(Color.White, radius = handleSize, center = rect.bottomLeft)
}

/**
 * 计算并限制矩形范围在图片尺寸内
 */
private fun calculateClampedRect(start: Offset, end: Offset, limitW: Int, limitH: Int): Rect {
    val left = min(start.x, end.x).coerceIn(0f, limitW.toFloat())
    val top = min(start.y, end.y).coerceIn(0f, limitH.toFloat())
    val right = max(start.x, end.x).coerceIn(0f, limitW.toFloat())
    val bottom = max(start.y, end.y).coerceIn(0f, limitH.toFloat())

    return Rect(left, top, right, bottom)
}