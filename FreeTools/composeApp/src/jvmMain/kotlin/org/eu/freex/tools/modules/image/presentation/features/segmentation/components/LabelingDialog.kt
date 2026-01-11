package org.eu.freex.tools.modules.image.presentation.features.segmentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.eu.freex.tools.modules.image.domain.model.SegmentationRect

@Composable
fun LabelingDialog(
    rect: SegmentationRect,
    sourceImage: ImageBitmap,
    initialText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember(initialText) { mutableStateOf(initialText) }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.padding(16.dp)) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(modifier = Modifier.size(120.dp).border(1.dp, Color.Gray)) {
                    SimpleSliceCanvas(sourceImage, rect, Modifier.fillMaxSize())
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("输入字符") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { onConfirm(text) }) {
                    Text("确认 (Enter)")
                }
            }
        }
    }
}

@Composable
private fun SimpleSliceCanvas(image: ImageBitmap, rect: SegmentationRect, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val rW = rect.width.toInt()
        val rH = rect.height.toInt()
        val safeW = rW.coerceAtMost(image.width - rect.left)
        val safeH = rH.coerceAtMost(image.height - rect.top)

        if (safeW > 0 && safeH > 0) {
            drawImage(
                image = image,
                srcOffset = IntOffset(rect.left, rect.top),
                srcSize = IntSize(safeW, safeH),
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                filterQuality = FilterQuality.None
            )
        }
    }
}