package org.eu.freex.tools.modules.image.presentation.features.tools.dialogs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.rememberWindowState
import org.eu.freex.tools.common.utils.ImageUtils
import java.awt.image.BufferedImage
import kotlin.math.max
import kotlin.math.min

@Composable
fun ScreenCropperDialog(
    image: BufferedImage? = null,
    onCropConfirm: (BufferedImage) -> Unit,
    onDismiss: () -> Unit
) {
    // 1. 【核心】全屏底图作为组件内部状态管理
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 全屏无边框窗口
    Window(
        onCloseRequest = onDismiss,
        title = "Screen Cropper",
        transparent = true,
        undecorated = true,
        alwaysOnTop = true,
        state = rememberWindowState(placement = WindowPlacement.Maximized)
    ) {
        // 3. 根据状态显示不同内容
        Box(modifier = Modifier.fillMaxSize().background(Color.Transparent)) {
            when {
                isLoading -> {
                    // 加载中：显示半透明背景和转圈
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                errorMessage != null -> {
                    // 错误提示
                    AlertDialog(
                        onDismissRequest = onDismiss,
                        title = { Text("截图失败") },
                        text = { Text(errorMessage ?: "未知错误") },
                        confirmButton = {
                            TextButton(onClick = onDismiss) { Text("关闭") }
                        }
                    )
                }
                image != null -> {
                    // 截图成功：显示裁剪画布
                    CropperCanvas(
                        fullScreenImage = image,
                        onCropConfirm = onCropConfirm,
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }
}

/**
 * 将裁剪画布逻辑提取出来，保持主流程清晰
 */
@Composable
private fun CropperCanvas(
    fullScreenImage: BufferedImage,
    onCropConfirm: (BufferedImage) -> Unit,
    onDismiss: () -> Unit
) {
    val displayBitmap = remember(fullScreenImage) { fullScreenImage.toComposeImageBitmap() }
    var selectionRect by remember { mutableStateOf<Rect?>(null) }
    var dragStart by remember { mutableStateOf(Offset.Zero) }

    Box(modifier = Modifier.fillMaxSize()) {
        // 画布区域
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { start: Offset ->
                            dragStart = start
                            selectionRect = Rect(start, start)
                        },
                        onDragEnd = { },
                        onDragCancel = { selectionRect = null },
                        onDrag = { change: PointerInputChange, _: Offset ->
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
                // 重绘选区内的清晰图
                drawImage(
                    image = displayBitmap,
                    srcOffset = IntOffset(rect.left.toInt(), rect.top.toInt()),
                    srcSize = IntSize(rect.width.toInt(), rect.height.toInt()),
                    dstOffset = IntOffset(rect.left.toInt(), rect.top.toInt()),
                    dstSize = IntSize(rect.width.toInt(), rect.height.toInt())
                )

                // 绘制边框
                drawRect(
                    color = Color.Red,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
                )
            }
        }

        // 底部操作栏
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
                FilledTonalButton(onClick = onDismiss) {
                    Text("取消")
                }

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