package org.eu.freex.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.common.AppModule
import org.eu.freex.tools.common.i18n.LocalStrings
import org.eu.freex.tools.common.theme.ThemeMode
import org.eu.freex.tools.modules.image.presentation.core.ExportDisplayImage
import org.eu.freex.tools.modules.image.presentation.core.ImageUiEvent
import org.eu.freex.tools.modules.image.presentation.core.LoadProject
import org.eu.freex.tools.modules.image.presentation.core.SaveProject
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
fun TopBar(
    currentModule: AppModule, // 【修复】使用 ToolModule
    themeMode: ThemeMode,
    onEvent: (ImageUiEvent) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onModuleChange: (AppModule) -> Unit
) {
    val backgroundColor = MaterialTheme.colorScheme.surface
    val contentColor = MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(backgroundColor)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "FreexTools Pro",
            color = contentColor,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.width(40.dp))

        // --- 模块导航 ---
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

        // --- 文件菜单 ---
        FileMenu(onEvent)

        // --- 主题切换 ---
        ThemeSwitcher(themeMode, onThemeChange, contentColor)
    }
}

@Composable
private fun ThemeSwitcher(currentMode: ThemeMode, onChange: (ThemeMode) -> Unit, color: Color) {
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
private fun ModuleTab(
    text: String,
    icon: ImageVector,
    isSelected: Boolean,
    baseColor: Color,
    onClick: () -> Unit
) {
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

@Composable
private fun FileMenu(onEvent: (ImageUiEvent) -> Unit) {
    val strings = LocalStrings.current // 获取当前语言包
    var expanded by remember { mutableStateOf(false) }

    Box {
        TextButton(onClick = { expanded = true }) {
            Text(strings.file, color = MaterialTheme.colorScheme.onSurface)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(strings.saveProject) },
                onClick = {
                    expanded = false
                    val file = showFileChooser(true, "FreeTools Project", "fxproj")
                    // 确保这里发送了 SaveProject 事件
                    if (file != null) onEvent(SaveProject(file))
                },
                leadingIcon = { Icon(Icons.Default.Save, null) }
            )

            DropdownMenuItem(
                text = { Text(strings.loadProject) },
                onClick = {
                    expanded = false
                    val file = showFileChooser(false, "FreeTools Project", "fxproj")
                    // 确保这里发送了 LoadProject 事件
                    if (file != null) onEvent(LoadProject(file))
                },
                leadingIcon = { Icon(Icons.Default.FolderOpen, null) }
            )

            HorizontalDivider()

            DropdownMenuItem(
                text = { Text("导出当前图片 (.png)") },
                onClick = {
                    expanded = false
                    val file = showFileChooser(true, "PNG Image", "png")
                    // 【修复】使用 ExportDisplayImage，无需传入 layer
                    if (file != null) onEvent(ExportDisplayImage(file))
                },
                leadingIcon = { Icon(Icons.Default.Image, null) }
            )
        }
    }
}

private fun showFileChooser(save: Boolean, filterDesc: String, ext: String): File? {
    val mode = if (save) FileDialog.SAVE else FileDialog.LOAD
    val dialog = FileDialog(null as Frame?, "$filterDesc (*.$ext)", mode)
    dialog.file = "*.$ext"
    dialog.setFilenameFilter { _, name -> name.endsWith(".$ext", ignoreCase = true) }
    dialog.isVisible = true
    val fileName = dialog.file
    val directory = dialog.directory
    if (fileName != null && directory != null) {
        var file = File(directory, fileName)
        if (save && !file.name.lowercase().endsWith(".$ext")) {
            file = File(file.parent, "${file.name}.$ext")
        }
        return file
    }
    return null
}