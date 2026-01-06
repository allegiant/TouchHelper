package org.eu.freex.tools.modules.image.presentation.features.tools.dialogs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.eu.freex.tools.modules.image.domain.model.ImageLayer
import java.awt.Rectangle

@Composable
fun ScreenCropperDialog(
    imageLayer: ImageLayer,
    onConfirm: (Rectangle) -> Unit,
    onDismiss: () -> Unit
) {
    val bitmap = remember(imageLayer) { imageLayer.image?.toComposeImageBitmap() } ?: return

    // 获取当前的屏幕密度
    val density = LocalDensity.current

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            var cropRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
            var startPoint by remember { mutableStateOf(Offset.Zero) }

            Image(
                bitmap = bitmap,
                contentDescription = null,
                // 建议：如果你是要做精确裁剪，Fit 可能会导致图片有黑边，导致坐标对应不上图片本身。
                // 如果是全屏截图，通常建议用 Crop 或 FillBounds，或者你需要计算图片在屏幕上的实际渲染区域。
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { startPoint = it },
                            onDrag = { change, _ ->
                                val cur = change.position
                                val left = minOf(startPoint.x, cur.x)
                                val top = minOf(startPoint.y, cur.y)
                                val right = maxOf(startPoint.x, cur.x)
                                val bottom = maxOf(startPoint.y, cur.y)
                                cropRect = androidx.compose.ui.geometry.Rect(left, top, right, bottom)
                            }
                        )
                    }
            )

            // --- 修复部分 ---
            if (!cropRect.isEmpty) {
                Box(
                    Modifier
                        // 关键修复1：使用 toDp() 将像素转为 Dp，而不是直接加 .dp
                        .offset(
                            x = with(density) { cropRect.left.toDp() },
                            y = with(density) { cropRect.top.toDp() }
                        )
                        // 关键修复2：宽高同理
                        .size(
                            width = with(density) { cropRect.width.toDp() },
                            height = with(density) { cropRect.height.toDp() }
                        )
                        .border(2.dp, Color.Red)
                        .background(Color.White.copy(0.2f))
                )
            }
            // ----------------

            Row(Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                Button(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(16.dp))
                Button(onClick = {
                    onConfirm(Rectangle(cropRect.left.toInt(), cropRect.top.toInt(), cropRect.width.toInt(), cropRect.height.toInt()))
                }, enabled = !cropRect.isEmpty) { Text("确认") }
            }
        }
    }
}