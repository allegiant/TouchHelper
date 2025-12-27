package org.eu.freex.tools.modules.image.presentation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.eu.freex.tools.model.WorkImage
import org.eu.freex.tools.utils.ColorUtils

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun EditorCanvas(
    modifier: Modifier = Modifier,
    workImage: WorkImage?,
    binaryPreview: WorkImage? = null,
    activeRects: List<Rect> = emptyList(),
    scale: Float,
    offset: Offset,
    hoverPos: IntOffset?,      // ViewModel 传来的参数（这里暂时忽略，改用本地状态）
    hoverColor: Color,         // ViewModel 传来的参数（这里暂时忽略）
    onTransformChange: (Float, Offset) -> Unit,
    onHover: (IntOffset?, Color) -> Unit,
    onRectClick: (Rect) -> Unit,
    onColorPick: (String) -> Unit
) {
    // 1. 捕获最新状态
    val currentScale by rememberUpdatedState(scale)
    val currentOffset by rememberUpdatedState(offset)
    val currentWorkImage by rememberUpdatedState(workImage)
    val currentRects by rememberUpdatedState(activeRects)

    val currentOnTransformChange by rememberUpdatedState(onTransformChange)
    val currentOnRectClick by rememberUpdatedState(onRectClick)

    // 【优化】使用本地状态处理悬停显示，实现 0 延迟跟手，不再依赖 ViewModel 回流
    var localHoverPos by remember { mutableStateOf<IntOffset?>(null) }
    var localHoverColor by remember { mutableStateOf(Color.Transparent) }

    // 2. 滚轮缩放逻辑
    val scrollModifier = Modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Scroll) {
                    val change = event.changes.first()
                    val scrollDelta = change.scrollDelta.y
                    val zoomFactor = if (scrollDelta > 0) 0.9f else 1.1f
                    val newScale = (currentScale * zoomFactor).coerceIn(0.1f, 50f)
                    currentOnTransformChange(newScale, currentOffset)
                    change.consume()
                }
            }
        }
    }

    // 3. 拖拽逻辑 (保持之前的锚点累积法)
    val dragModifier = Modifier.pointerInput(Unit) {
        var startOffset = Offset.Zero
        var accumulatedDrag = Offset.Zero

        detectDragGestures(
            onDragStart = {
                startOffset = currentOffset
                accumulatedDrag = Offset.Zero
            },
            onDragEnd = { accumulatedDrag = Offset.Zero }
        ) { change, dragAmount ->
            change.consume()
            accumulatedDrag += dragAmount
            currentOnTransformChange(currentScale, startOffset + accumulatedDrag)
        }
    }

    // 4. 【修复悬停取色】使用 onPointerEvent (Desktop 专用) + 本地状态
    // onPointerEvent 比 pointerInput 更独立，不容易被拖拽手势拦截
    val hoverModifier = Modifier
        .onPointerEvent(PointerEventType.Move) { event ->
            val pos = event.changes.first().position
            val imgX = ((pos.x - currentOffset.x) / currentScale).toInt()
            val imgY = ((pos.y - currentOffset.y) / currentScale).toInt()

            val bufImg = currentWorkImage?.bufferedImage
            if (bufImg != null && imgX >= 0 && imgX < bufImg.width && imgY >= 0 && imgY < bufImg.height) {
                val rgb = bufImg.getRGB(imgX, imgY)
                val color = Color(rgb)

                // 立即更新本地 UI
                localHoverPos = IntOffset(imgX, imgY)
                localHoverColor = color
            } else {
                localHoverPos = null
            }
        }
        .onPointerEvent(PointerEventType.Exit) {
            localHoverPos = null
        }

    // 5. 点击逻辑
    val tapModifier = Modifier.pointerInput(Unit) {
        detectTapGestures { tapOffset ->
            val imgX = (tapOffset.x - currentOffset.x) / currentScale
            val imgY = (tapOffset.y - currentOffset.y) / currentScale

            val clickedRect = currentRects.find { rect ->
                imgX >= rect.left && imgX <= rect.right &&
                        imgY >= rect.top && imgY <= rect.bottom
            }
            if (clickedRect != null) {
                currentOnRectClick(clickedRect)
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .background(Color(0xFF1E1E1E))
            .fillMaxSize()
            .clipToBounds() // 【关键修复 1】解决图片移出边界覆盖其他组件的问题
            .then(scrollModifier)
            .then(hoverModifier) // 【关键修复 2】使用 onPointerEvent 处理悬停
            .then(dragModifier)
            .then(tapModifier)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            withTransform({
                translate(offset.x, offset.y)
                scale(scale, pivot = Offset.Zero)
            }) {
                // 绘制底图
                workImage?.let { img ->
                    drawImage(img.bitmap)
                }

                // 绘制二值化预览
                binaryPreview?.let { bin ->
                    drawImage(bin.bitmap, alpha = 0.8f)
                }

                // 绘制切割框
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

        // --- 悬停信息浮层 (使用本地状态) ---
        if (localHoverPos != null) {
            PixelMagnifier(
                modifier = Modifier.align(Alignment.TopEnd),
                color = localHoverColor,
                pos = localHoverPos!!
            )
        }
    }
}

@Composable
private fun PixelMagnifier(
    modifier: Modifier,
    color: Color,
    pos: IntOffset
) {
    Surface(
        modifier = modifier.padding(8.dp),
        color = Color.Black.copy(alpha = 0.7f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
        elevation = 4.dp
    ) {
        Column(Modifier.padding(8.dp)) {
            Text(
                "X: ${pos.x}, Y: ${pos.y}",
                color = Color.White,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(16.dp).background(color).border(1.dp, Color.White))
                Spacer(Modifier.width(8.dp))
                Text(
                    "#${ColorUtils.colorToHex(color)}",
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }
    }
}