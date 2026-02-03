package org.eu.freex.tools.modules.image.presentation.features.editor.layers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.rememberTextMeasurer
import org.eu.freex.tools.modules.image.domain.model.FeaturePoint
import org.eu.freex.tools.modules.image.presentation.features.feature.components.drawFeaturePointsOverlay

@Composable
fun FeatureLayer(
    points: List<FeaturePoint>
) {
    val textMeasurer = rememberTextMeasurer()

    // [修改] 删除了 Box 和 pointerInput，因为它只负责“看”，不负责“动”
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawFeaturePointsOverlay(
            points = points,
            textMeasurer = textMeasurer
        )
    }
}