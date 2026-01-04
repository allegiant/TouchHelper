package org.eu.freex.tools.modules.image.presentation.features.tools.dialogs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.* // 引入 Material3
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerInputChange // 【关键】添加导入
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.rememberWindowState
import java.awt.image.BufferedImage
import kotlin.math.max
import kotlin.math.min
import org.eu.freex.tools.common.utils.ImageUtils

@Composable
fun ScreenCropperDialog(
    fullScreenImage: BufferedImage,
    onCropConfirm: (BufferedImage) -> Unit,
    onDismiss: () -> Unit
) {
    val displayBitmap = remember(fullScreenImage) { fullScreenImage.toComposeImageBitmap() }
    var selectionRect by remember { mutableStateOf<Rect?>(null) }
    var dragStart by remember { mutableStateOf(Offset.Zero) }

    // 全屏无边框窗口
    Window(
        onCloseRequest = onDismiss,
        title = "Screen Cropper",
        transparent = true, // 透明窗口以显示截图
        undecorated = true, // 无边框
        alwaysOnTop = true,
        // 窗口状态通过 state 参数控制
        state = rememberWindowState(placement = WindowPlacement.Maximized)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. 画布区域：绘制底图 + 遮罩 + 选区
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            // 【修复】显式指定类型 start: Offset
                            onDragStart = { start: Offset ->
                                dragStart = start
                                selectionRect = Rect(start, start)
                            },
                            onDragEnd = { },
                            onDragCancel = { selectionRect = null },
                            // 【修复】显式指定类型 change: PointerInputChange, dragAmount: Offset
                            onDrag = { change: PointerInputChange, _: Offset ->
                                // 实时更新选区
                                val current = change.position
                                val left = min(dragStart.x, current.x)
                                val top = min(dragStart.y, current.y)
                                val right = max(dragStart.x, current.x)
                                val bottom = max(dragStart.y, current.y)
                                selectionRect = Rect(left, top, right, bottom)
                            }
                        )
                    }
            ) {
                // 绘制全屏截图
                drawImage(displayBitmap)

                // 绘制半透明黑色遮罩
                drawRect(Color.Black.copy(alpha = 0.6f))

                // 绘制选区
                selectionRect?.let { rect ->
                    // 1. 在选区位置重绘清晰的原图 (看起来像是把遮罩擦除了)
                    drawImage(
                        image = displayBitmap,
                        srcOffset = IntOffset(rect.left.toInt(), rect.top.toInt()),
                        srcSize = IntSize(rect.width.toInt(), rect.height.toInt()),
                        dstOffset = IntOffset(rect.left.toInt(), rect.top.toInt()),
                        dstSize = IntSize(rect.width.toInt(), rect.height.toInt())
                    )

                    // 2. 绘制选区边框 (红色虚线)
                    drawRect(
                        color = Color.Red,
                        topLeft = rect.topLeft,
                        size = rect.size,
                        style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
                    )
                }
            }

            // 2. 底部操作栏
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(32.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shadowElevation = 8.dp,
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 取消按钮
                    FilledTonalButton(onClick = onDismiss) {
                        Text("取消")
                    }

                    // 确认按钮
                    Button(
                        enabled = selectionRect != null && selectionRect!!.width > 5,
                        onClick = {
                            selectionRect?.let {
                                onCropConfirm(ImageUtils.cropImage(fullScreenImage, it))
                            }
                        }
                    ) {
                        Text("确认裁剪")
                    }
                }
            }
        }
    }
}