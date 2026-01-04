package org.eu.freex.tools.modules.image.presentation.features.editor

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.modules.image.domain.model.WorkImage

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun EditorCanvas(
    modifier: Modifier = Modifier,
    workImage: WorkImage?,
    // 【关键修改】接收 UI 层状态对象，替代 ViewModel 数据
    state: EditorState = rememberEditorState(),
) {
    val binaryPreview: WorkImage? = null
    val currentWorkImage by rememberUpdatedState(workImage)
    // 依然使用 UpdatedState 防止闭包捕获旧 State 引用
    val currentState by rememberUpdatedState(state)

    // 滚轮缩放逻辑
    val scrollModifier = Modifier.onPointerEvent(PointerEventType.Scroll) {
        val change = it.changes.first()
        val scrollDelta = change.scrollDelta.y
        val zoomFactor = if (scrollDelta > 0) 0.9f else 1.1f
        // 直接修改 UI 状态
        currentState.zoom(zoomFactor)
    }

    // 拖拽逻辑
    val smoothDragModifier = Modifier.pointerInput(Unit) {
        detectDragGestures { change, dragAmount ->
            change.consume()
            // 直接修改 UI 状态
            currentState.pan(dragAmount)
        }
    }

    // 悬浮取色逻辑
    val hoverModifier = Modifier.onPointerEvent(PointerEventType.Move) {
        val pos = it.changes.first().position
        val scale = currentState.mainScale
        val offset = currentState.mainOffset

        val imgX = ((pos.x - offset.x) / scale).toInt()
        val imgY = ((pos.y - offset.y) / scale).toInt()

        val bufImg = currentWorkImage?.bufferedImage
        if (bufImg != null && imgX in 0 until bufImg.width && imgY in 0 until bufImg.height) {
            val rgb = bufImg.getRGB(imgX, imgY)
            val color = Color(rgb)
            // 直接更新 UI 状态，不走 ViewModel
            currentState.updateHover(IntOffset(imgX, imgY), color)
        } else {
            currentState.updateHover(null, Color.Transparent)
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
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clip(RectangleShape)
            .then(scrollModifier)
            .then(smoothDragModifier)
            .then(hoverModifier)
    ) {
        // 初始加载时居中图片
        LaunchedEffect(workImage) {
            if (workImage != null) {
                val img = workImage.bufferedImage
                val canvasW = constraints.maxWidth
                val canvasH = constraints.maxHeight

                // 计算初始缩放比例（比如适应屏幕）
                // 这里暂时保持 1.0f 或者你可以加 logic 自动 fit
                val targetScale = state.mainScale

                val imgDisplayW = img.width * targetScale
                val imgDisplayH = img.height * targetScale
                val centerX = (canvasW - imgDisplayW) / 2f
                val centerY = (canvasH - imgDisplayH) / 2f

                // 重置视图
                state.reset(targetScale, Offset(centerX, centerY))
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            withTransform({
                translate(state.mainOffset.x, state.mainOffset.y)
                scale(state.mainScale, pivot = Offset.Zero)
            }) {
                mainBitmap?.let { img -> drawImage(img) }
                previewBitmap?.let { bin -> drawImage(bin, alpha = 0.8f) }
            }
        }

        if (state.hoverPixelPos != null) {
            PixelMagnifier(
                modifier = Modifier.align(Alignment.TopEnd),
                color = state.hoverColor,
                pos = state.hoverPixelPos!!
            )
        }
    }
}

@Composable
private fun PixelMagnifier(modifier: Modifier, color: Color, pos: IntOffset) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f)
    val contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    Surface(
        modifier = modifier.padding(8.dp),
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(4.dp),
        shadowElevation = 4.dp
    ) {
        Column(Modifier.padding(8.dp)) {
            Text("X: ${pos.x}, Y: ${pos.y}", style = MaterialTheme.typography.labelSmall)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(12.dp)
                        .background(color)
                        .border(1.dp, borderColor)
                )
                Spacer(Modifier.width(4.dp))

                val hex = "#%02X%02X%02X".format(
                    color.red.times(255).toInt(),
                    color.green.times(255).toInt(),
                    color.blue.times(255).toInt()
                )

                Text(hex, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}