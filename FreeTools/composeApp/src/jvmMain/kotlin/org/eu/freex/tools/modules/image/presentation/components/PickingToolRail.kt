package org.eu.freex.tools.modules.image.presentation.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.common.model.PickingToolState

/**
 * 左侧窄工具栏
 */
@Composable
fun PickingToolRail(
    activeTool: PickingToolState,
    onToolSelect: (PickingToolState) -> Unit,
    onCapture: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationRail(
        modifier = modifier.width(56.dp).fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        header = {
            // 固定操作：截图 (对应截图最上面的相机图标)
            // 如果有导入按钮，也可以放在这里
            NavigationRailItem(
                selected = false,
                onClick = onCapture,
                icon = { Icon(Icons.Default.CameraAlt, "截图") },
                colors = actionItemColors()
            )
            Spacer(Modifier.height(8.dp))
            // 2. [新增] 导入按钮
            NavigationRailItem(
                selected = false,
                onClick = onImport,
                icon = { Icon(Icons.Default.FileOpen, "导入图片") },
                colors = actionItemColors()
            )

            Spacer(Modifier.height(8.dp))
        }
    ) {
        // 定义要显示的工具列表 (按截图顺序排列)
        val tools = listOf(
            PickingToolState.None,         // 1. 指针 (默认)
            PickingToolState.RegionPicker, // 2. 选取范围
            PickingToolState.ColorPicker,  // 3. 取色
            PickingToolState.PointPicker   // 4. 取点
            // 未来可以继续添加：铅笔、橡皮擦等...
        )

        // 遍历列表动态生成按钮
        tools.forEach { tool ->
            NavigationRailItem(
                selected = activeTool::class == tool::class,
                onClick = { onToolSelect(tool) },
                icon = { Icon(tool.icon, contentDescription = tool.desc) },
                colors = toolItemColors()
            )
        }
    }
}

// 工具类按钮的样式 (选中时高亮)
@Composable
private fun toolItemColors() = NavigationRailItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
    indicatorColor = MaterialTheme.colorScheme.secondaryContainer
)

// 操作类按钮的样式 (通常不显示选中态)
@Composable
private fun actionItemColors() = NavigationRailItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.onSurface,
    indicatorColor = Color.Transparent // 透明指示器，使其看起来像普通按钮
)