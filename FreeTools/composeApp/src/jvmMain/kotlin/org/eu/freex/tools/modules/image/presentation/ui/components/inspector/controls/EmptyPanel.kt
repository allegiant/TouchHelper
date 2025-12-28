package org.eu.freex.tools.modules.image.presentation.ui.components.inspector.controls

import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EmptyPanel() {
    Text(
        text = "当前滤镜无可调参数",
        color = Color.DarkGray,
        fontSize = 12.sp,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}