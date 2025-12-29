// 文件: composeApp/src/jvmMain/kotlin/org/eu/freex/tools/TopBar.kt
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
import org.eu.freex.tools.model.AppModule
import org.eu.freex.tools.modules.image.presentation.contract.ImageUiEvent
import org.eu.freex.tools.modules.image.presentation.viewmodel.ImageViewModel
import org.eu.freex.tools.theme.ThemeMode
import org.eu.freex.tools.utils.ImageUtils
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

// enum class AppModule 保持不变 (如果在其他文件没定义，请保留在这里)
// enum class AppModule { IMAGE_PROCESSING, FONT_MANAGER }

@Composable
fun TopBar(
    currentModule: AppModule,
    themeMode: ThemeMode,
    viewModel: ImageViewModel, // 传入 ViewModel 以便发送事件
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

        FileMenu(viewModel)

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

@Composable
fun FileMenu(viewModel: ImageViewModel) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        TextButton(onClick = { expanded = true }) {
            Text("文件", color = MaterialTheme.colorScheme.onSurface)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("保存工程 (.fxproj)") },
                onClick = {
                    expanded = false
                    ImageUtils.pickFile()
                    val file = showFileChooser(save = true, filterDesc = "FreeTools Project", ext = "fxproj")
                    if (file != null) viewModel.handleEvent(ImageUiEvent.SaveProject(file))
                },
                leadingIcon = { Icon(Icons.Default.Save, null) }
            )

            DropdownMenuItem(
                text = { Text("打开工程") },
                onClick = {
                    expanded = false
                    val file = showFileChooser(save = false, filterDesc = "FreeTools Project", ext = "fxproj")
                    if (file != null) viewModel.handleEvent(ImageUiEvent.LoadProject(file))
                },
                leadingIcon = { Icon(Icons.Default.FolderOpen, null) }
            )

            HorizontalDivider()

            DropdownMenuItem(
                text = { Text("导出当前图片 (.png)") },
                onClick = {
                    expanded = false
                    val file = showFileChooser(save = true, filterDesc = "PNG Image", ext = "png")
                    if (file != null) viewModel.handleEvent(ImageUiEvent.ExportImage(file))
                }
            )
        }
    }
}

// 使用原生 AWT FileDialog 的文件选择器
fun showFileChooser(save: Boolean, filterDesc: String, ext: String): File? {
    val mode = if (save) FileDialog.SAVE else FileDialog.LOAD

    // 1. 创建 FileDialog (parent 传 null 会使用默认隐藏 Frame)
    // title 使用 filterDesc 可以让用户知道要选什么文件
    val dialog = FileDialog(null as Frame?, "$filterDesc (*.$ext)", mode)

    // 2. 设置过滤器
    // 设置默认文件名模式 (这对 Windows 有效，能过滤显示文件)
    dialog.file = "*.$ext"
    // 设置逻辑过滤器 (这对 macOS/Linux 有效)
    dialog.setFilenameFilter { _, name -> name.endsWith(".$ext", ignoreCase = true) }

    // 3. 显示对话框 (这行代码会阻塞线程，直到用户关闭对话框)
    dialog.isVisible = true

    // 4. 获取结果
    val fileName = dialog.file
    val directory = dialog.directory

    // 如果 fileName 为 null，说明用户点击了取消
    if (fileName != null && directory != null) {
        var file = File(directory, fileName)

        // 5. 自动补全后缀 (仅在保存模式下)
        if (save && !file.name.lowercase().endsWith(".$ext")) {
            file = File(file.parent, "${file.name}.$ext")
        }
        return file
    }
    return null
}