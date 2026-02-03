package org.eu.freex.tools.modules.image.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.modules.image.presentation.viewmodel.EditorCanvasViewModel
import org.jetbrains.skia.Font
import org.jetbrains.skia.Paint
import org.jetbrains.skia.TextLine
import org.koin.compose.koinInject
import kotlin.math.ceil
import kotlin.math.floor

/**
 * 带标尺的画布容器
 * 布局：
 * [Corner] [Top Ruler]
 * [Left  ] [Content  ]
 * [Ruler ] [         ]
 */
@Composable
fun RulerCanvasContainer(
    modifier: Modifier = Modifier,
    // [关键] 需要 ViewModel 来获取画布的缩放和平移状态
    viewModel: EditorCanvasViewModel = koinInject(),
    content: @Composable () -> Unit
) {
    val transform by viewModel.transformState.collectAsState()
    val rulerSize = 24.dp
    val rulerColor = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier.fillMaxSize()) {
        // 1. 上半部分：左上角空白块 + 顶部标尺
        Row(Modifier.fillMaxWidth().height(rulerSize)) {
            // 左上角空白块 (单位显示)
            Box(
                Modifier
                    .width(rulerSize)
                    .fillMaxHeight()
                    .background(rulerColor)
            )

            // 顶部标尺 (X轴)
            HorizontalRuler(
                modifier = Modifier.weight(1f).fillMaxHeight().background(rulerColor),
                scale = transform.scale,
                offset = transform.pan.x,
                textColor = textColor
            )
        }

        // 2. 下半部分：左侧标尺 + 画布内容
        Row(Modifier.weight(1f)) {
            // 左侧标尺 (Y轴)
            VerticalRuler(
                modifier = Modifier.width(rulerSize).fillMaxHeight().background(rulerColor),
                scale = transform.scale,
                offset = transform.pan.y,
                textColor = textColor
            )

            // 画布内容区
            Box(Modifier.weight(1f).fillMaxHeight()) {
                content()
            }
        }
    }
}

/**
 * 水平标尺 (Top Ruler)
 */
@Composable
private fun HorizontalRuler(
    modifier: Modifier,
    scale: Float,
    offset: Float,
    textColor: Color
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        // 计算刻度间距
        val (step, labelStep) = calculateSteps(scale)

        // 计算可视区域对应的图片坐标范围
        // screenX = imageX * scale + offset
        // imageX = (screenX - offset) / scale
        val startImageX = (-offset / scale).coerceAtLeast(0f)
        val endImageX = ((width - offset) / scale)

        // 对齐到最近的刻度
        val startTick = (floor(startImageX / step) * step).toInt()
        val endTick = (ceil(endImageX / step) * step).toInt()

        // 绘制 Native Text 需要的 Paint
        val paint = Paint().apply {
            color = textColor.toArgb()
            isAntiAlias = true
        }
        val font = Font().apply { size = 10.dp.toPx() } // 字体大小

        for (i in startTick..endTick step step) {
            // 将图片坐标转换回屏幕坐标进行绘制
            val screenX = i * scale + offset

            // 超出绘制范围跳过
            if (screenX < 0 || screenX > width) continue

            val isLabelTick = i % labelStep == 0
            val lineHeight = if (isLabelTick) height * 0.5f else height * 0.25f

            // 画刻度线 (从底部向上画)
            drawLine(
                color = textColor,
                start = Offset(screenX, height),
                end = Offset(screenX, height - lineHeight),
                strokeWidth = 1f
            )

            // 画数字
            if (isLabelTick) {
                val text = i.toString()
                val line = TextLine.make(text, font)
                drawContext.canvas.nativeCanvas.drawTextLine(
                    line,
                    screenX + 2f, // 稍微向右偏移
                    height - lineHeight + 2f, // 稍微向下偏移
                    paint
                )
            }
        }
    }
}

/**
 * 垂直标尺 (Left Ruler)
 */
@Composable
private fun VerticalRuler(
    modifier: Modifier,
    scale: Float,
    offset: Float,
    textColor: Color
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        val (step, labelStep) = calculateSteps(scale)

        val startImageY = (-offset / scale).coerceAtLeast(0f)
        val endImageY = ((height - offset) / scale)

        val startTick = (floor(startImageY / step) * step).toInt()
        val endTick = (ceil(endImageY / step) * step).toInt()

        val paint = Paint().apply {
            color = textColor.toArgb()
            isAntiAlias = true
        }
        val font = Font().apply { size = 10.dp.toPx() }

        for (i in startTick..endTick step step) {
            val screenY = i * scale + offset

            if (screenY < 0 || screenY > height) continue

            val isLabelTick = i % labelStep == 0
            val lineWidth = if (isLabelTick) width * 0.5f else width * 0.25f

            // 画刻度线 (从右向左画)
            drawLine(
                color = textColor,
                start = Offset(width, screenY),
                end = Offset(width - lineWidth, screenY),
                strokeWidth = 1f
            )

            // 画数字 (文字旋转90度或者直接横着写，这里直接横着写)
            if (isLabelTick) {
                val text = i.toString()
                // 垂直标尺空间小，只画主要数字，且可能需要简略
                if (text.length <= 4) { // 太长就不画了
                    val line = TextLine.make(text, font)
                    // 简单的垂直居中
                    drawContext.canvas.nativeCanvas.drawTextLine(
                        line,
                        2f,
                        screenY + 10f, // 稍微向下调整作为基线
                        paint
                    )
                }
            }
        }
    }
}

/**
 * 辅助函数：根据缩放倍率计算刻度间隔
 * 返回 Pair(小刻度, 大刻度/数字刻度)
 */
private fun calculateSteps(scale: Float): Pair<Int, Int> {
    return when {
        scale >= 4f -> 1 to 10        // 放大很大：每1px一个刻度，每10px一个数字
        scale >= 2f -> 5 to 10        // 放大较多：每5px一个刻度
        scale >= 1f -> 10 to 50       // 正常：每10px一个刻度，每50px一个数字
        scale >= 0.5f -> 50 to 100    // 缩小：每50px一个刻度
        else -> 100 to 500            // 缩小很多
    }
}