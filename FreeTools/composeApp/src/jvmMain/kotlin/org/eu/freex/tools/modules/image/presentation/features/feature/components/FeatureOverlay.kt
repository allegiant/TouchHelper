package org.eu.freex.tools.modules.image.presentation.features.feature.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.eu.freex.tools.modules.image.domain.model.FeaturePoint

/**
 * 在画布上绘制特征点 (抓抓模式)
 */
fun DrawScope.drawFeaturePointsOverlay(
    points: List<FeaturePoint>,
    textMeasurer: TextMeasurer
) {
    val crossSize = 10f // 十字大小
    val strokeWidth = 2f

    val textStyle = TextStyle(
        color = Color.Green,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        shadow = Shadow(color = Color.Black, blurRadius = 2f)
    )

    points.forEachIndexed { index, point ->
        val x = point.x.toFloat()
        val y = point.y.toFloat()

        // 1. 绘制十字准星 (绿色，带黑色描边以增强对比度)
        // 先画黑底
        drawLine(Color.Black, Offset(x - crossSize, y), Offset(x + crossSize, y), strokeWidth + 2f)
        drawLine(Color.Black, Offset(x, y - crossSize), Offset(x, y + crossSize), strokeWidth + 2f)
        // 再画绿芯
        drawLine(Color.Green, Offset(x - crossSize, y), Offset(x + crossSize, y), strokeWidth)
        drawLine(Color.Green, Offset(x, y - crossSize), Offset(x, y + crossSize), strokeWidth)

        // 2. 绘制编号 (P1, P2...)
        val text = "P${index + 1}"
        val textResult = textMeasurer.measure(text, textStyle)

        drawText(
            textLayoutResult = textResult,
            topLeft = Offset(x + 5f, y - 20f)
        )
    }
}