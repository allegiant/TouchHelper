package org.eu.freex.tools.modules.image.presentation.features.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.modules.image.domain.model.FeaturePoint
import org.eu.freex.tools.modules.image.presentation.viewmodel.EditorCanvasViewModel

@Composable
fun FeatureExtractionPanel(
    modifier: Modifier = Modifier,
    viewModel: EditorCanvasViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    Column(modifier = modifier.padding(8.dp)) {
        // 标题栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "已取特征点 (${uiState.featurePoints.size})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            TextButton(onClick = { viewModel.clearFeaturePoints() }) {
                Text("清空", color = MaterialTheme.colorScheme.error)
            }
        }

        // 列表区
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.featurePoints) { point ->
                FeaturePointItem(
                    point = point,
                    onDelete = { viewModel.removeFeaturePoint(point.id) }
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // 代码生成区
        Button(
            onClick = {
                val code = generateCmpColorCode(uiState.featurePoints)
                clipboardManager.setText(AnnotatedString(code))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("复制多点找色代码")
        }
    }
}

@Composable
fun FeaturePointItem(point: FeaturePoint, onDelete: () -> Unit) {
    val color = remember(point.colorHex) {
        try {
            val hex = point.colorHex.removePrefix("#")
            Color(hex.substring(0, 2).toInt(16), hex.substring(2, 4).toInt(16), hex.substring(4, 6).toInt(16))
        } catch (e: Exception) { Color.Gray }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(color, RoundedCornerShape(4.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("${point.x}, ${point.y}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
            Text(point.colorHex, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
        }
    }
}

private fun generateCmpColorCode(points: List<FeaturePoint>): String {
    if (points.isEmpty()) return ""
    val first = points.first()
    val sb = StringBuilder()
    sb.append("if (images.detectsColor(img, \"${first.colorHex}\", ${first.x}, ${first.y})) {\n")
    if (points.size > 1) {
        sb.append("    // 更多点位检测逻辑...\n")
    }
    sb.append("    // TODO: Action\n")
    sb.append("}")
    return sb.toString()
}