package org.eu.freex.tools.modules.image.presentation.ui.components


import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.eu.freex.tools.model.WorkImage
import org.eu.freex.tools.utils.ColorUtils


/**
 * 2. 中间画布 (EditorCanvas)
 * 这个组件负责显示图片、绘制切割框、处理缩放拖拽和鼠标悬停取色
 */

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun EditorCanvas(
    modifier: Modifier = Modifier,
    workImage: WorkImage?,          // 1. 当前要显示的图
    binaryPreview: WorkImage? = null, // 2. 【关键修正】这里必须有 binaryPreview 参数，且类型为 WorkImage?
    activeRects: List<Rect> = emptyList(), // 3. 切割框列表
    scale: Float,
    offset: Offset,
    hoverPos: IntOffset?,
    hoverColor: Color,
    onTransformChange: (Float, Offset) -> Unit,
    onHover: (IntOffset?, Color) -> Unit,
    onRectClick: (Rect) -> Unit,
    onColorPick: (String) -> Unit
) {
    // 处理滚轮缩放
    val scrollModifier = Modifier.onPointerEvent(PointerEventType.Scroll) {
        val change = it.changes.first()
        val scrollDelta = change.scrollDelta.y
        val zoomFactor = if (scrollDelta > 0) 0.9f else 1.1f
        val newScale = (scale * zoomFactor).coerceIn(0.1f, 20f)
        onTransformChange(newScale, offset)
    }

    // 处理拖拽平移
    val dragModifier = Modifier.pointerInput(Unit) {
        detectDragGestures { change, dragAmount ->
            change.consume()
            onTransformChange(scale, offset + dragAmount)
        }
    }

    // 处理点击 (点击红框)
    val tapModifier = Modifier.pointerInput(Unit) {
        detectTapGestures { tapOffset ->
            val imgX = (tapOffset.x - offset.x) / scale
            val imgY = (tapOffset.y - offset.y) / scale

            val clickedRect = activeRects.find { rect ->
                imgX >= rect.left && imgX <= rect.right &&
                        imgY >= rect.top && imgY <= rect.bottom
            }
            if (clickedRect != null) {
                onRectClick(clickedRect)
            }
        }
    }

    // 处理鼠标移动 (悬停取色)
    val hoverModifier = Modifier.onPointerEvent(PointerEventType.Move) {
        val pos = it.changes.first().position
        val imgX = ((pos.x - offset.x) / scale).toInt()
        val imgY = ((pos.y - offset.y) / scale).toInt()

        val bufImg = workImage?.bufferedImage
        if (bufImg != null && imgX in 0 until bufImg.width && imgY in 0 until bufImg.height) {
            val rgb = bufImg.getRGB(imgX, imgY)
            val color = Color(rgb)
            onHover(IntOffset(imgX, imgY), color)
        } else {
            onHover(null, Color.Transparent)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .background(Color(0xFF1E1E1E))
            .then(scrollModifier)
            .then(dragModifier)
            .then(tapModifier)
            .then(hoverModifier)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            withTransform({
                translate(offset.x, offset.y)
                scale(scale, pivot = Offset.Zero)
            }) {
                // 1. 绘制底图
                workImage?.let { img ->
                    drawImage(img.bitmap)
                }

                // 2. 绘制二值化预览 (如果有)
                // 修正：确保这里使用的是 binaryPreview?.bitmap
                binaryPreview?.let { bin ->
                    drawImage(bin.bitmap, alpha = 0.8f)
                }

                // 3. 绘制切割框 (红框)
                activeRects.forEach { rect ->
                    drawRect(
                        color = Color.Red,
                        topLeft = Offset(rect.left, rect.top),
                        size = androidx.compose.ui.geometry.Size(rect.width, rect.height),
                        style = Stroke(width = 1.5f / scale)
                    )
                }
            }
        }

        // --- 悬停信息浮层 ---
        if (hoverPos != null) {
            PixelMagnifier(
                modifier = Modifier.align(Alignment.TopEnd),
                color = hoverColor,
                pos = hoverPos
            )
        }
    }
}

// 简单的悬停信息组件
@Composable
private fun PixelMagnifier(
    modifier: Modifier,
    color: Color,
    pos: IntOffset
) {
    Surface(
        modifier = modifier.padding(8.dp),
        color = Color.Black.copy(alpha = 0.7f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
    ) {
        Column(Modifier.padding(8.dp)) {
            Text(
                "X: ${pos.x}, Y: ${pos.y}",
                color = Color.White,
                fontSize = 10.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(color).border(1.dp, Color.White))
                Spacer(Modifier.width(4.dp))
                // 需要确保 ColorUtils.colorToHex 方法存在，或者直接用 String.format
                Text(
                    "#${ColorUtils.colorToHex(color)}",
                    color = Color.White,
                    fontSize = 10.sp
                )
            }
        }
    }
}