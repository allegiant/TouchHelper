package org.eu.freex.tools.modules.image.presentation.features.filter.components.renderers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.common.components.HelpTooltip
import org.eu.freex.tools.modules.image.domain.model.DeskewFilter
import org.eu.freex.tools.modules.image.domain.model.ImageFilter

object DeskewRenderer : FilterRenderer {

    @Composable
    override fun Content(
        filter: ImageFilter,
        onFilterChange: (ImageFilter) -> Unit
    ) {
        val current = filter as? DeskewFilter ?: return

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // 标题栏
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "倾斜矫正 (Deskew)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                HelpTooltip(description = "旋转图像以校正倾斜的文本行。支持基于霍夫变换的自动检测。")
            }

            // 1. 自动检测开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("自动检测角度", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "基于水平线特征自动计算",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = current.isAuto,
                    onCheckedChange = { onFilterChange(current.copy(isAuto = it)) }
                )
            }

            // 2. 手动角度滑块 (仅在非自动模式下显示，或者允许在自动基础上微调？
            // 这里逻辑设定为：如果 Auto=true，后端会忽略 angle 参数。
            // 所以 UI 上如果开启了 Auto，最好禁用或隐藏滑块，以免用户困惑。)
            AnimatedVisibility(
                visible = !current.isAuto,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("旋转角度", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${String.format("%.1f", current.angle)}°",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = current.angle,
                        onValueChange = { onFilterChange(current.copy(angle = it)) },
                        valueRange = -20f..20f, // 限制在 +/- 20度，通常够用了
                        steps = 39, // 允许 1度 的步进 (或者去掉 steps 允许平滑滚动)
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            // 3. 填充颜色选择 (黑/白)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onFilterChange(current.copy(splitBackgroundColor = !current.splitBackgroundColor)) }
            ) {
                Checkbox(
                    checked = current.splitBackgroundColor,
                    onCheckedChange = null, // 事件由 Row 处理
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                )
                Text(
                    text = "使用白色填充背景 (默认黑色)",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}