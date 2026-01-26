package org.eu.freex.tools.modules.image.presentation.features.segmentation.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.eu.freex.tools.modules.image.domain.model.SegmentationProject

/**
 * [SegmentationOverlay]
 * 切割识别模式下的覆盖层绘制逻辑。
 */
fun DrawScope.drawSegmentationOverlay(
    project: SegmentationProject?,
    textMeasurer: TextMeasurer, // [新增] 传入测量器
    selectedIndex: Int = -1
) {
    if (project == null) return

    val textStyle = TextStyle(
        color = Color.Yellow,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        background = Color.Black.copy(alpha = 0.5f) // 加个半透明背景，防止看不清
    )

    project.results.forEachIndexed { index, segRect ->
        val isSelected = index == selectedIndex
        val color = if (isSelected) Color.Yellow else Color.Red
        val strokeWidth = if (isSelected) 2.dp.toPx() else 1.dp.toPx()

        val rectTopLeft = Offset(segRect.left.toFloat(), segRect.top.toFloat())

        // 1. 绘制矩形框
        drawRect(
            color = color,
            topLeft = rectTopLeft,
            size = Size(segRect.width.toFloat(), segRect.height.toFloat()),
            style = Stroke(width = strokeWidth)
        )

        // 2. [已实现] 绘制编号 (仅绘制选中的，或者是全部绘制但只在框够大时显示)
        // 为了不让画面太乱，我们设定：只有 "选中项" 或者 "鼠标悬停项" 才显示文字
        // 这里演示：只显示选中项
        if (isSelected) {
            val text = "${index + 1}"
            val textLayoutResult = textMeasurer.measure(text, textStyle)

            // 将文字画在矩形框的左上角上方
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(
                    rectTopLeft.x,
                    rectTopLeft.y - textLayoutResult.size.height - 2f
                )
            )
        }
    }
}