package org.eu.freex.tools.modules.image.presentation.components.panel.sections

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BinarizationPreview(
    binaryBitmap: ImageBitmap?, // 改为直接传 Bitmap
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, Color(0xFF4CAF50))
                .background(Color(0xFF1E1E1E)),
            contentAlignment = Alignment.Center
        ) {
            if (binaryBitmap != null) {
                Image(
                    bitmap = binaryBitmap,
                    contentDescription = "Binary Result",
                    modifier = Modifier.fillMaxSize(), // 这里的缩放模式看需求，通常 Fit 或 FillBounds
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.None
                )
            } else {
                // 空状态提示
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("未设定截图区域", color = Color.Gray, fontSize = 12.sp)
                    Text("请使用裁剪工具框选目标", color = Color.DarkGray, fontSize = 10.sp)
                }
            }
        }
    }
}