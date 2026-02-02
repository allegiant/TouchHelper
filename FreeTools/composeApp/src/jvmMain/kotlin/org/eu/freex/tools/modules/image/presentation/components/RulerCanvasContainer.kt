package org.eu.freex.tools.modules.image.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 带标尺的画布容器
 * 布局：
 * Top Ruler
 * L    Content (EditorCanvasPanel)
 * e
 * f
 * t
 */
@Composable
fun RulerCanvasContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Top Ruler
        Box(Modifier.fillMaxWidth().height(24.dp)) {
            // TODO: Step 3 实现标尺绘制
        }

        Row(Modifier.weight(1f)) {
            // Left Ruler
            Box(Modifier.width(24.dp).fillMaxHeight()) {
                // TODO: Step 3 实现标尺绘制
            }

            // Canvas Content
            Box(Modifier.weight(1f)) {
                content()
            }
        }
    }
}