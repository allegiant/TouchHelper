package org.eu.freex.tools.modules.image.presentation.features.tools.dialogs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.eu.freex.tools.modules.image.domain.model.ImageLayer

@Composable
fun ScreenCropperDialog(
    imageLayer: ImageLayer,
    onConfirm: (IntRect) -> Unit, // 这里传出的将是最终的物理像素坐标
    onDismiss: () -> Unit
) {
    val bitmap = remember(imageLayer) { imageLayer.image?.toComposeImageBitmap() } ?: return
    val density = LocalDensity.current

    // 记录图片组件在屏幕上的实际渲染尺寸
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            var cropRect by remember { mutableStateOf(Rect.Zero) }
            var startPoint by remember { mutableStateOf(Offset.Zero) }

            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.FillBounds, // 强制填满，配合独立计算的 ScaleX/Y，可以解决变形带来的坐标问题
                modifier = Modifier.fillMaxSize()
                    .onSizeChanged { viewSize = it } // 关键步骤：获取渲染大小
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { startPoint = it },
                            onDrag = { change, _ ->
                                val cur = change.position
                                val left = minOf(startPoint.x, cur.x)
                                val top = minOf(startPoint.y, cur.y)
                                val right = maxOf(startPoint.x, cur.x)
                                val bottom = maxOf(startPoint.y, cur.y)
                                cropRect = Rect(left, top, right, bottom)
                            }
                        )
                    }
            )

            if (!cropRect.isEmpty) {
                Box(
                    Modifier
                        .offset(
                            x = with(density) { cropRect.left.toDp() },
                            y = with(density) { cropRect.top.toDp() }
                        )
                        .size(
                            width = with(density) { cropRect.width.toDp() },
                            height = with(density) { cropRect.height.toDp() }
                        )
                        .border(2.dp, Color.Red)
                        .background(Color.White.copy(0.2f))
                )
            }

            Row(Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                Button(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(16.dp))
                Button(
                    onClick = {
                        // --- 核心修复逻辑 ---
                        if (viewSize.width > 0 && viewSize.height > 0) {
                            // 计算 View 到 Bitmap 的映射比例
                            // 即使 View 因为标题栏被压扁了，scaleY 也会自动补偿回来
                            val scaleX = bitmap.width.toDouble() / viewSize.width.toDouble()
                            val scaleY = bitmap.height.toDouble() / viewSize.height.toDouble()

                            // 计算最终坐标 (使用 Float)
                            val topLeft = IntOffset((cropRect.left * scaleX).toInt(), (cropRect.top * scaleY).toInt())
                            val bottomRight =
                                IntOffset((cropRect.right * scaleX).toInt(), (cropRect.bottom * scaleY).toInt())

                            val finalRect = IntRect(topLeft, bottomRight)
                            onConfirm(finalRect)
                        }
                    },
                    enabled = !cropRect.isEmpty
                ) { Text("确认") }
            }
        }
    }
}