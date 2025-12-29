// 文件: composeApp/src/jvmMain/kotlin/org/eu/freex/tools/TopBar.kt
package org.eu.freex.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.model.AppModule
import org.eu.freex.tools.theme.ThemeMode

// enum class AppModule 保持不变 (如果在其他文件没定义，请保留在这里)
// enum class AppModule { IMAGE_PROCESSING, FONT_MANAGER }

@Composable
fun TopBar(
    currentModule: AppModule,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onModuleChange: (AppModule) -> Unit
) {
    // M3 中通常使用 SurfaceContainer 或 Surface 作为顶栏背景
    val backgroundColor = MaterialTheme.colorScheme.surface
    val contentColor = MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp) // M3 标准高度通常稍高，也可以保持 48.dp
            .background(backgroundColor)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "FreexTools Pro",
            color = contentColor,
            style = MaterialTheme.typography.titleMedium, // M3 排版样式
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.width(40.dp))

        // 导航标签
        ModuleTab(
            text = "图像处理",
            icon = Icons.Default.Image,
            isSelected = currentModule == AppModule.IMAGE_PROCESSING,
            baseColor = contentColor,
            onClick = { onModuleChange(AppModule.IMAGE_PROCESSING) }
        )

        Spacer(Modifier.width(20.dp))

        ModuleTab(
            text = "字库管理",
            icon = Icons.Default.FontDownload,
            isSelected = currentModule == AppModule.FONT_MANAGER,
            baseColor = contentColor,
            onClick = { onModuleChange(AppModule.FONT_MANAGER) }
        )

        Spacer(Modifier.weight(1f))

        // 主题切换按钮
        ThemeSwitcher(themeMode, onThemeChange, contentColor)
    }
}

@Composable
fun ThemeSwitcher(currentMode: ThemeMode, onChange: (ThemeMode) -> Unit, color: Color) {
    val (icon, tooltip) = when (currentMode) {
        ThemeMode.Light -> Icons.Default.Brightness7 to "浅色模式"
        ThemeMode.Dark -> Icons.Default.Brightness4 to "深色模式"
        ThemeMode.System -> Icons.Default.BrightnessAuto to "跟随系统"
    }

    IconButton(onClick = {
        val nextMode = when (currentMode) {
            ThemeMode.System -> ThemeMode.Light
            ThemeMode.Light -> ThemeMode.Dark
            ThemeMode.Dark -> ThemeMode.System
        }
        onChange(nextMode)
    }) {
        Icon(icon, contentDescription = tooltip, tint = color)
    }
}

@Composable
fun ModuleTab(
    text: String,
    icon: ImageVector,
    isSelected: Boolean,
    baseColor: Color,
    onClick: () -> Unit
) {
    // 选中态使用 Primary 色，未选中态使用 OnSurface 但降低透明度
    val color = if (isSelected) MaterialTheme.colorScheme.primary else baseColor.copy(alpha = 0.6f)

    Row(
        modifier = Modifier
            .fillMaxHeight()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}