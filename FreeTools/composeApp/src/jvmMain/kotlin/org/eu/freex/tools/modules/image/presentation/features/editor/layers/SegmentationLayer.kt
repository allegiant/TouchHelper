package org.eu.freex.tools.modules.image.presentation.features.editor.layers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import org.eu.freex.tools.modules.image.presentation.features.segmentation.components.drawSegmentationOverlay
import org.eu.freex.tools.modules.image.presentation.viewmodel.SegmentationViewModel
import kotlin.math.floor

@Composable
fun SegmentationLayer(
    viewModel: SegmentationViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val textMeasurer = rememberTextMeasurer()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val x = floor(offset.x).toInt()
                        val y = floor(offset.y).toInt()
                        viewModel.onCanvasTap(x, y)
                    },
                    onDoubleTap = {
                        viewModel.showLabelDialog()
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawSegmentationOverlay(
                project = uiState.project,
                textMeasurer = textMeasurer,
                selectedIndex = uiState.selectedIndex
            )
        }
    }
}