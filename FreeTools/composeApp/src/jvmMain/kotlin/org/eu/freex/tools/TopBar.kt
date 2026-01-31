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
import androidx.compose.material.icons.filled.DocumentScanner // [新增]
import androidx.compose.material.icons.filled.FlashOn
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
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.core.PickerMode
import io.github.vinceglb.filekit.core.PickerType
import org.eu.freex.tools.common.i18n.LocalStrings
import org.eu.freex.tools.common.model.AppModule
import org.eu.freex.tools.common.theme.ThemeMode
import org.koin.dsl.module
import java.io.File

@Composable
fun TopBar(
    currentModule: AppModule,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onModuleChange: (AppModule) -> Unit,
    onLoadProject: (File) -> Unit,
    onSaveProject: (File) -> Unit,
    onExportImage: (File) -> Unit,
    // [新增] 识别测试的回调
    onRecognitionTest: (File) -> Unit
) {
    val backgroundColor = MaterialTheme.colorScheme.surface
    val contentColor = MaterialTheme.colorScheme.onSurface

    // [新增] 识别测试图片选择器
    val recognitionTester = rememberFilePickerLauncher(
        title = "选择测试图片",
        mode = PickerMode.Single,
        type = PickerType.Image // 限制为图片
    ) { platformFile ->
        platformFile?.file?.let { onRecognitionTest(it) }
    }

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
        ModuleTab(
            text = "抓抓工具",
            icon = Icons.Default.FlashOn,
            isSelected = currentModule == AppModule.PICKING_TOOL,
            baseColor = contentColor,
            onClick = { onModuleChange(AppModule.PICKING_TOOL) }
        )

        Spacer(Modifier.weight(1f))

        // [修复] 移除了错误的 RecognitionScreen 调用，改为一个功能按钮
        // 如果你需要它作为工具菜单，也可以像 FileMenu 一样封装，这里先用图标按钮
        IconButton(onClick = { recognitionTester.launch() }) {
            Icon(
                imageVector = Icons.Default.DocumentScanner,
                contentDescription = "OCR 测试",
                tint = contentColor
            )
        }

        Spacer(Modifier.width(8.dp))

        // --- 文件菜单 ---
        FileMenu(
            onLoadProject = onLoadProject,
            onSaveProject = onSaveProject,
            onExportImage = onExportImage
        )

        // --- 主题切换 ---
        ThemeSwitcher(themeMode, onThemeChange, contentColor)
    }
}

// ... ThemeSwitcher 和 ModuleTab 保持不变 ...
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
    }) { Icon(icon, contentDescription = tooltip, tint = color) }
}

@Composable
private fun ModuleTab(text: String, icon: ImageVector, isSelected: Boolean, baseColor: Color, onClick: () -> Unit) {
    val color = if (isSelected) MaterialTheme.colorScheme.primary else baseColor.copy(alpha = 0.6f)
    Row(modifier = Modifier.fillMaxHeight().clickable(onClick = onClick).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text = text, color = color, style = MaterialTheme.typography.bodyMedium, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun FileMenu(
    onLoadProject: (File) -> Unit,
    onSaveProject: (File) -> Unit,
    onExportImage: (File) -> Unit
) {
    val strings = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }

    // --- 1. 加载工程 ---
    val projectLoader = rememberFilePickerLauncher(
        type = PickerType.File(extensions = listOf("fxproj")),
        mode = PickerMode.Single,
        title = "打开工程文件"
    ) { platformFile ->
        platformFile?.file?.let { onLoadProject(it) } // 直接调用回调
    }

    // --- 2. 保存工程 ---
    val projectSaver = rememberFileSaverLauncher { platformFile ->
        platformFile?.file?.let { onSaveProject(it) } // 直接调用回调
    }

    // --- 3. 导出图片 ---
    val imageExporter = rememberFileSaverLauncher { platformFile ->
        platformFile?.file?.let { onExportImage(it) } // 直接调用回调
    }

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
                    projectSaver.launch(baseName = "project", extension = "fxproj")
                },
                leadingIcon = { Icon(Icons.Default.Save, null) }
            )

            DropdownMenuItem(
                text = { Text(strings.loadProject) },
                onClick = {
                    expanded = false
                    projectLoader.launch()
                },
                leadingIcon = { Icon(Icons.Default.FolderOpen, null) }
            )

            HorizontalDivider()

            DropdownMenuItem(
                text = { Text("导出当前图片 (.png)") },
                onClick = {
                    expanded = false
                    imageExporter.launch(baseName = "export", extension = "png")
                },
                leadingIcon = { Icon(Icons.Default.Image, null) }
            )
        }
    }
}