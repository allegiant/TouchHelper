package org.eu.freex.tools.modules.image.presentation.features.filter.components.renderers

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.modules.image.domain.model.GrayscaleFilter
import org.eu.freex.tools.modules.image.domain.model.GrayscaleMode
import org.eu.freex.tools.modules.image.domain.model.ImageFilter

object GrayscaleRenderer : FilterRenderer {

    @Composable
    override fun Content(filter: ImageFilter, onFilterChange: (ImageFilter) -> Unit) {
        val current = filter as? GrayscaleFilter ?: return

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {

            // 1. 标准模式
            ModeItem(
                title = "标准混合 (通用推荐)",
                desc = "最稳妥的选择。还原人眼看到的黑白效果，适合绝大多数游戏UI文字。",
                mode = GrayscaleMode.WEIGHTED,
                current = current,
                onChange = onFilterChange
            )

            // 2. 绿色通道
            ModeItem(
                title = "绿色通道 (高画质)",
                desc = "安卓模拟器截图中，G通道通常画质损失最小。如果字库笔画模糊，试选这个。这是因为很多截图采用YUV格式，绿色分量保留最完整。",
                mode = GrayscaleMode.GREEN,
                current = current,
                onChange = onFilterChange
            )

            // 3. 最大值 (针对游戏白字)
            ModeItem(
                title = "亮色优先 (去半透明底)",
                desc = "专治“黑底白字”。直接提取最亮像素，能“过滤”掉游戏常见的半透明深色背景，让文字主体更清晰。",
                mode = GrayscaleMode.MAX,
                current = current,
                onChange = onFilterChange
            )

            // 4. 红色通道
            ModeItem(
                title = "红色通道 (特殊)",
                desc = "适合提取红色内容（如：血量、暴击数字、红名），同时会过滤掉背景中的蓝色和绿色干扰。",
                mode = GrayscaleMode.RED,
                current = current,
                onChange = onFilterChange
            )

            // 5. 最小值
            ModeItem(
                title = "暗色优先 (针对书信/深色字)",
                desc = "适合处理游戏内的“白底黑字”内容（如：任务卷轴、信件、公告）。它会忽略背景的亮色，强制保留最暗的像素，防止深色文字在浅色背景中变淡。",
                mode = GrayscaleMode.MIN,
                current = current,
                onChange = onFilterChange
            )
        }
    }

    @Composable
    private fun ModeItem(
        title: String,
        desc: String,
        mode: GrayscaleMode,
        current: GrayscaleFilter,
        onChange: (ImageFilter) -> Unit
    ) {
        val selected = current.mode == mode
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .selectable(
                    selected = selected,
                    onClick = { onChange(current.copy(mode = mode)) },
                    role = Role.RadioButton
                )
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = MaterialTheme.colorScheme.outline
                )
            )

            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f)
            )

            if (desc.isNotEmpty()) {
                HelpTooltip(desc)
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun HelpTooltip(description: String) {
        TooltipArea(
            tooltip = {
                Surface(
                    modifier = Modifier.widthIn(max = 320.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shadowElevation = 6.dp,
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.12f)
                    )
                ) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,

                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            },
            delayMillis = 300,
            tooltipPlacement = TooltipPlacement.CursorPoint(
                alignment = Alignment.BottomEnd,
                offset = DpOffset(0.dp, 16.dp)
            )
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                contentDescription = "说明",
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}