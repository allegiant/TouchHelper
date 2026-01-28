package org.eu.freex.tools.modules.image.presentation.features.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import org.eu.freex.tools.modules.image.domain.model.ImageLayer
import org.eu.freex.tools.modules.image.presentation.features.editor.strategies.CanvasTabStrategy
import org.eu.freex.tools.modules.image.presentation.features.editor.utils.CanvasUtils
import org.eu.freex.tools.modules.image.presentation.features.editor.utils.CanvasUtils.conditional

/**
 * [EditorCanvasContent]
 * 核心渲染组件 (Dumb Component)。
 * 它不包含任何业务逻辑，完全由 [strategy] 驱动。
 */
@Composable
fun EditorCanvasContent(
    modifier: Modifier = Modifier,
    displayImage: ImageLayer?,
    strategy: CanvasTabStrategy,
    scale: Float,
    offset: Offset,
    // [新增] 变换回调
    onTransform: (zoomChange: Float, panChange: Offset) -> Unit
) {
    // === 1. 画布状态 ===
    var hoverPos by remember { mutableStateOf<Offset?>(null) }
    val textMeasurer = rememberTextMeasurer()

    // === 2. 手势处理 ===
    // 只有当策略允许缩放平移时，才启用 Transformable
    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        if (strategy.enableZoomPan) {
            onTransform(zoomChange, panChange)
        }
    }

    // 辅助颜色
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerLowest
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .clipToBounds()
            .pointerHoverIcon(strategy.getCursorIcon()) // 动态光标
            // 缩放平移手势
            .conditional(strategy.enableZoomPan) {
                transformable(state = transformableState)
            }
            // 鼠标悬停监听 (用于放大镜/坐标显示)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull()
                        if (event.type == PointerEventType.Move && change != null) {
                            hoverPos = change.position
                        } else if (event.type == PointerEventType.Exit) {
                            hoverPos = null
                        }
                    }
                }
            }
            // 点击手势
            .pointerInput(displayImage, strategy, scale, offset) {
                detectTapGestures(
                    onTap = { screenPos ->
                        handleTap(screenPos, displayImage, size.toSize(), scale, offset) { details ->
                            strategy.onTap(details.pixelPos.x, details.pixelPos.y, details.color)
                        }
                    },
                    onDoubleTap = { screenPos ->
                        handleTap(screenPos, displayImage, size.toSize(), scale, offset) { details ->
                            strategy.onDoubleTap(details.pixelPos.x, details.pixelPos.y)
                        }
                    }
                )
            }
    ) {
        val canvasSize = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())

        if (displayImage?.image != null) {
            val bufferedImage = displayImage.image
            val bitmap = remember(bufferedImage) { bufferedImage.toComposeImageBitmap() }
            val imageWidth = bufferedImage.width
            val imageHeight = bufferedImage.height
            val imageSize = IntSize(imageWidth, imageHeight)

            // === 3. 核心绘制层 (Canvas) ===
            Canvas(modifier = Modifier.fillMaxSize()) {
                // 3.1 绘制背景网格 (可选，这里简化为纯色或简单线条)
                drawRect(gridColor)

                // 3.2 应用变换 (缩放 + 平移)
                withTransform({
                    translate(left = offset.x + center.x, top = offset.y + center.y)
                    scale(scale, Offset.Zero)
                    translate(left = -imageWidth / 2f, top = -imageHeight / 2f)
                }) {
                    // 3.3 绘制底图
                    drawImage(bitmap, filterQuality = FilterQuality.None)

                    // 3.4 [策略钩子] 绘制业务 Overlay (在图片坐标系下)
                    with(strategy) {
                        drawOverlay(textMeasurer)
                    }
                }
            }

            // === 4. 组合层 Overlay (如框选组件) ===
            // 这些组件通常覆盖在 Canvas 之上，可能处理自己的手势
            strategy.ContentOverlay(modifier = Modifier.fillMaxSize())

            // === 5. 悬浮工具 (放大镜 / 信息条) ===
            if (hoverPos != null) {
                val pixelPos = CanvasUtils.screenToImage(hoverPos!!, canvasSize, imageSize, scale, offset)
                val inBounds = pixelPos.x in 0 until imageWidth && pixelPos.y in 0 until imageHeight

                strategy.HoverOverlay(
                    modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).zIndex(190f),
                    image = bufferedImage,
                    screenPos = hoverPos!!,
                    pixelPos = pixelPos,
                    inBounds = inBounds
                )
            }
        } else {
            Text(
                "请导入图片",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

// [新增] 私有辅助函数，减少代码重复
// 必须放在文件顶层或类内部，这里作为文件级私有函数
private fun handleTap(
    screenPos: Offset,
    displayImage: ImageLayer?,
    canvasSize: Size,
    scale: Float,
    offset: Offset,
    onResult: (CanvasUtils.TapDetails) -> Unit
) {
    val details = CanvasUtils.calculateTapDetails(
        screenPos,
        displayImage,
        canvasSize,
        scale,
        offset
    )
    if (details != null) {
        onResult(details)
    }
}