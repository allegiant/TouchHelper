package org.eu.freex.tools.modules.image.presentation.features.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.eu.freex.tools.modules.image.presentation.features.editor.behaviors.*
import org.eu.freex.tools.modules.image.presentation.features.editor.components.DefaultHoverInfoOverlay
import org.eu.freex.tools.modules.image.presentation.features.editor.components.MagnifierOverlay
import org.eu.freex.tools.modules.image.presentation.features.feature.components.RegionSelectorOverlay
import java.awt.image.BufferedImage

/**
 * 渲染全屏交互覆盖层 (Overlay Content Slot)
 * 对应 [RegionSelectorOverlay] 等全屏组件
 */
@Composable
fun BoxScope.EditorBehaviorOverlay(behavior: EditorBehavior) {
    when (behavior) {
        is RegionSelectingBehavior -> {
            // [具体实现] 使用你上传的 RegionSelectorOverlay
            RegionSelectorOverlay(
                modifier = Modifier.matchParentSize().zIndex(100f),
                onRegionSelected = behavior.onEnd,
                onCancel = behavior.onCancel
            )
        }
        // 其他模式如果没有全屏覆盖层，这里就留空
        else -> Unit
    }
}

/**
 * 渲染鼠标悬浮层 (Hover Content Slot)
 * 对应 [MagnifierOverlay] 和 [DefaultHoverInfoOverlay]
 */
@Composable
fun BoxScope.EditorHoverOverlay(
    behavior: EditorBehavior,
    image: BufferedImage,
    screenPos: Offset,
    pixelPos: IntOffset,
    inBounds: Boolean
) {
    // 1. 如果是取色模式，且在图片范围内，显示放大镜
    if (behavior is PickingBehavior && inBounds) {
        Box(modifier = Modifier.zIndex(200f)) {
            MagnifierOverlay(
                sourceImage = image,
                centerPixel = pixelPos,
                screenPos = screenPos,
                zoomLevel = 12, // 可以根据需要调整
                gridSize = 15
            )
        }
    }
    // 2. 否则，显示默认的左下角信息条 (坐标+颜色)
    else {
        DefaultHoverInfoOverlay(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
                .zIndex(190f), // 确保在最上层
            pixelPos = pixelPos,
            inBounds = inBounds,
            image = image
        )
    }
}