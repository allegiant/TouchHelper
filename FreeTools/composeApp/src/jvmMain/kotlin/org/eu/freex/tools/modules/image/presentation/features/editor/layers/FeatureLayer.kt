package org.eu.freex.tools.modules.image.presentation.features.editor.layers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import org.eu.freex.tools.common.utils.ImageUtils
import org.eu.freex.tools.modules.image.presentation.features.feature.components.drawFeaturePointsOverlay
import org.eu.freex.tools.modules.image.presentation.viewmodel.PickingToolViewModel
import org.koin.compose.koinInject
import java.awt.image.BufferedImage
import kotlin.math.floor

@Composable
fun FeatureLayer(
    viewModel: PickingToolViewModel,
    sourceImage: BufferedImage
) {
    val textMeasurer = rememberTextMeasurer()

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 处理点击：添加特征点
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val x = floor(offset.x).toInt()
                    val y = floor(offset.y).toInt()

                    if (x in 0 until sourceImage.width && y in 0 until sourceImage.height) {
                        val color = ImageUtils.getPixelColor(sourceImage, x, y)
                        viewModel.addPoint(x, y, color)
                    }
                }
            }
    ) {
        // 绘制逻辑复用之前的 drawFeaturePointsOverlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawFeaturePointsOverlay(
                points = viewModel.featurePoints.value,
                textMeasurer = textMeasurer
            )
        }
    }
}