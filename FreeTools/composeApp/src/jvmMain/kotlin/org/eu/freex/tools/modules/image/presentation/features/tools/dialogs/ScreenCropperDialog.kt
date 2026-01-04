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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.onSizeChanged
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
    // 1. 状态管理
    var isLoading by remember(image) { mutableStateOf(image == null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(image) {
        if (image != null) {
            isLoading = false
        }
    }

    // 全屏窗口
    Window(
        onCloseRequest = onDismiss,
        title = "Screen Cropper",
        transparent = true,
        undecorated = true,
        alwaysOnTop = true,
        // 保持 Fullscreen 以确保覆盖全屏
        state = rememberWindowState(placement = WindowPlacement.Fullscreen)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Transparent)) {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                errorMessage != null -> {
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

@Composable
private fun CropperCanvas(
    fullScreenImage: BufferedImage,
    onCropConfirm: (BufferedImage) -> Unit,
    onDismiss: () -> Unit
) {
    val displayBitmap = remember(fullScreenImage) { fullScreenImage.toComposeImageBitmap() }
    var selectionRect by remember { mutableStateOf<Rect?>(null) }
    var dragStart by remember { mutableStateOf(Offset.Zero) }

    // 【核心修复1】记录 Canvas 的实际渲染尺寸（像素），用于计算缩放比
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it } // 获取当前组件的像素尺寸
    ) {
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
            val canvasW = size.width
            val canvasH = size.height

            // 防止除以0错误
            if (canvasW <= 0 || canvasH <= 0) return@Canvas

            // 【核心修复2】计算缩放比例：图片尺寸 / 画布尺寸
            val scaleX = fullScreenImage.width.toFloat() / canvasW
            val scaleY = fullScreenImage.height.toFloat() / canvasH

            // 1. 绘制全屏截图（强制拉伸填满画布，解决缩放和留白问题）
            drawImage(
                image = displayBitmap,
                dstSize = IntSize(canvasW.toInt(), canvasH.toInt())
            )

            // 2. 绘制半透明黑色遮罩
            drawRect(Color.Black.copy(alpha = 0.6f))

            // 3. 绘制选区（高亮部分）
            selectionRect?.let { rect ->
                // 【核心修复3】绘制选区内容时，源坐标需要映射回原图坐标系
                // srcOffset/Size 是原图上的位置，需要乘 scale
                // dstOffset/Size 是屏幕上的位置，直接用 rect

                // 简单的边界保护
                val srcLeft = (rect.left * scaleX).coerceAtLeast(0f)
                val srcTop = (rect.top * scaleY).coerceAtLeast(0f)
                val srcWidth = (rect.width * scaleX).coerceAtMost(fullScreenImage.width - srcLeft)
                val srcHeight = (rect.height * scaleY).coerceAtMost(fullScreenImage.height - srcTop)

                if (srcWidth > 0 && srcHeight > 0) {
                    drawImage(
                        image = displayBitmap,
                        srcOffset = IntOffset(srcLeft.toInt(), srcTop.toInt()),
                        srcSize = IntSize(srcWidth.toInt(), srcHeight.toInt()),
                        dstOffset = IntOffset(rect.left.toInt(), rect.top.toInt()),
                        dstSize = IntSize(rect.width.toInt(), rect.height.toInt())
                    )
                }

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
                        selectionRect?.let { rect ->
                            // 【核心修复4】最终裁剪时，将屏幕坐标映射回原图坐标
                            if (canvasSize.width > 0 && canvasSize.height > 0) {
                                val sX = fullScreenImage.width.toFloat() / canvasSize.width
                                val sY = fullScreenImage.height.toFloat() / canvasSize.height

                                val mappedRect = Rect(
                                    left = rect.left * sX,
                                    top = rect.top * sY,
                                    right = rect.right * sX,
                                    bottom = rect.bottom * sY
                                )
                                onCropConfirm(ImageUtils.cropImage(fullScreenImage, mappedRect))
                            }
                        }
                    }
                ) {
                    Text("确认裁剪")
                }
            }
        }
    }
}