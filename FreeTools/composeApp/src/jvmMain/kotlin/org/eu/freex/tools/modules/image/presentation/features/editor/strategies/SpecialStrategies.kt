package org.eu.freex.tools.modules.image.presentation.features.editor.strategies

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.eu.freex.tools.modules.image.domain.model.RecognitionResult
import org.eu.freex.tools.modules.image.presentation.features.feature.components.RegionSelectorOverlay

/**
 * [RegionSelectionStrategy]
 * 区域框选策略。
 * 特性：
 * 1. 禁用画布自带的缩放平移 (enableZoomPan = false)，防止手势冲突。
 * 2. 使用 ContentOverlay 加载现有的 RegionSelectorOverlay 组件。
 */
class RegionSelectionStrategy(
    private val onRegionSelected: (IntRect) -> Unit,
    private val onCancel: () -> Unit
) : CanvasTabStrategy {

    // 关键：禁用缩放平移，让 RegionSelectorOverlay 接管所有触控
    override val enableZoomPan: Boolean = false

    @Composable
    override fun ContentOverlay(modifier: Modifier) {
        // 加载现有的框选组件，层级设为最高
        RegionSelectorOverlay(
            modifier = modifier.fillMaxSize().zIndex(100f),
            onRegionSelected = onRegionSelected,
            onCancel = onCancel
        )
    }
}

/**
 * [RecognitionStrategy]
 * OCR 识别结果展示策略。
 * 特性：
 * 1. 允许缩放平移，方便查看细节。
 * 2. 在 DrawScope 中绘制红框。
 */
class RecognitionStrategy(
    private val results: List<RecognitionResult>
) : CanvasTabStrategy {

    override fun DrawScope.drawOverlay(textMeasurer: TextMeasurer) {
        val strokeWidth = 2.dp.toPx()

        results.forEach { result ->
            val rect = result.rect
            // 绘制识别框
            drawRect(
                color = Color.Red,
                topLeft = Offset(rect.left.toFloat(), rect.top.toFloat()),
                size = Size(rect.width.toFloat(), rect.height.toFloat()),
                style = Stroke(width = strokeWidth)
            )

            // 如果需要，这里也可以绘制简单的文字标签
            // 或者在 ContentOverlay 中绘制复杂的交互式标签
        }
    }
}