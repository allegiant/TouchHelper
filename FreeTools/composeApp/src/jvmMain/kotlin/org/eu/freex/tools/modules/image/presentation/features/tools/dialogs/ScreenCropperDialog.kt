package org.eu.freex.tools.modules.image.presentation.features.tools.dialogs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.eu.freex.tools.modules.image.domain.model.ImageLayer
import java.awt.Rectangle

@Composable
fun ScreenCropperDialog(
    imageLayer: ImageLayer,
    onConfirm: (Rectangle) -> Unit,
    onDismiss: () -> Unit
) {
    val bitmap = remember(imageLayer) { imageLayer.image?.toComposeImageBitmap() } ?: return
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            var cropRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
            var startPoint by remember { mutableStateOf(Offset.Zero) }

            Image(
                bitmap = bitmap, null, Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { startPoint = it },
                            onDrag = { change, _ ->
                                val cur = change.position
                                val left = minOf(startPoint.x, cur.x); val top = minOf(startPoint.y, cur.y)
                                val right = maxOf(startPoint.x, cur.x); val bottom = maxOf(startPoint.y, cur.y)
                                cropRect = androidx.compose.ui.geometry.Rect(left, top, right, bottom)
                            }
                        )
                    },
                contentScale = ContentScale.Fit
            )
            if (!cropRect.isEmpty) {
                Box(Modifier.offset(cropRect.left.dp, cropRect.top.dp)
                    .size(cropRect.width.dp, cropRect.height.dp)
                    .border(2.dp, Color.Red).background(Color.White.copy(0.2f)))
            }
            Row(Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                Button(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(16.dp))
                Button(onClick = {
                    onConfirm(Rectangle(cropRect.left.toInt(), cropRect.top.toInt(), cropRect.width.toInt(), cropRect.height.toInt()))
                }, enabled = !cropRect.isEmpty) { Text("确认") }
            }
        }
    }
}