package org.eu.freex.tools.modules.image.presentation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip // 新增：用于裁剪
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape // 新增：裁剪形状
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.eu.freex.tools.model.WorkImage

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun EditorCanvas(
    modifier: Modifier = Modifier,
    workImage: WorkImage?,
    binaryPreview: WorkImage? = null,
    scale: Float,
    offset: Offset,
    hoverPos: IntOffset?,
    hoverColor: Color,
    onTransformChange: (Float, Offset) -> Unit,
    onHover: (IntOffset?, Color) -> Unit,
    onColorPick: (String) -> Unit
) {
    val currentScale by rememberUpdatedState(scale)
    val currentOffset by rememberUpdatedState(offset)
    val currentOnTransformChange by rememberUpdatedState(onTransformChange)

    val scrollModifier = Modifier.onPointerEvent(PointerEventType.Scroll) {
        val change = it.changes.first()
        val scrollDelta = change.scrollDelta.y
        val zoomFactor = if (scrollDelta > 0) 0.9f else 1.1f
        val newScale = (currentScale * zoomFactor).coerceIn(0.1f, 20f)
        currentOnTransformChange(newScale, currentOffset)
    }

    // 拖拽逻辑 (保持之前的修复)
    val smoothDragModifier = Modifier.pointerInput(Unit) {
        var startDragOffset = Offset.Zero
        var dragOffsetAccumulator = Offset.Zero

        detectDragGestures(
            onDragStart = {
                startDragOffset = currentOffset
                dragOffsetAccumulator = Offset.Zero
            },
            onDrag = { change, dragAmount ->
                change.consume()
                dragOffsetAccumulator += dragAmount
                val targetOffset = startDragOffset + dragOffsetAccumulator
                currentOnTransformChange(currentScale, targetOffset)
            }
        )
    }

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

    val mainBitmap = remember(workImage) {
        workImage?.bufferedImage?.toComposeImageBitmap()
    }

    val previewBitmap = remember(binaryPreview) {
        binaryPreview?.bufferedImage?.toComposeImageBitmap()
    }

    BoxWithConstraints(
        modifier = modifier
            .background(Color(0xFF1E1E1E))
            .clip(RectangleShape) // 【关键修复】将内容裁剪至组件边界内
            .then(scrollModifier)
            .then(smoothDragModifier)
            .then(hoverModifier)
    ) {
        LaunchedEffect(workImage) {
            if (workImage != null) {
                val img = workImage.bufferedImage
                val canvasW = constraints.maxWidth
                val canvasH = constraints.maxHeight
                val imgDisplayW = img.width * scale
                val imgDisplayH = img.height * scale
                val centerX = (canvasW - imgDisplayW) / 2f
                val centerY = (canvasH - imgDisplayH) / 2f
                onTransformChange(scale, Offset(centerX, centerY))
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            withTransform({
                translate(offset.x, offset.y)
                scale(scale, pivot = Offset.Zero)
            }) {
                mainBitmap?.let { img -> drawImage(img) }
                previewBitmap?.let { bin -> drawImage(bin, alpha = 0.8f) }
            }
        }

        if (hoverPos != null) {
            PixelMagnifier(
                modifier = Modifier.align(Alignment.TopEnd),
                color = hoverColor,
                pos = hoverPos
            )
        }
    }
}

@Composable
private fun PixelMagnifier(modifier: Modifier, color: Color, pos: IntOffset) {
    Surface(
        modifier = modifier.padding(8.dp),
        color = Color.Black.copy(alpha = 0.7f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
    ) {
        Column(Modifier.padding(8.dp)) {
            Text("X: ${pos.x}, Y: ${pos.y}", color = Color.White, fontSize = 10.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(color).border(1.dp, Color.White))
                Spacer(Modifier.width(4.dp))
                val hex = "#%02X%02X%02X".format(color.red.times(255).toInt(), color.green.times(255).toInt(), color.blue.times(255).toInt())
                Text(hex, color = Color.White, fontSize = 10.sp)
            }
        }
    }
}