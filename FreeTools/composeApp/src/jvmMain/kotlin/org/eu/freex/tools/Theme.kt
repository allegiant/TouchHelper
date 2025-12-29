// 文件: composeApp/src/jvmMain/kotlin/org/eu/freex/tools/Theme.kt
package org.eu.freex.tools

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 定义三种模式状态
enum class ThemeMode {
    Light, Dark, System
}

// M3 浅色方案
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6200EE),
    onPrimary = Color.White,
    secondary = Color(0xFF03DAC5),
    onSecondary = Color.Black,
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1B1F),
    background = Color(0xFFF0F0F0),
    onBackground = Color(0xFF1C1B1F),
    surfaceContainer = Color(0xFFF0F0F0) // M3 新增，用于容器背景
)

// M3 深色方案
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFBB86FC),
    onPrimary = Color.Black,
    secondary = Color(0xFF03DAC5),
    onSecondary = Color.Black,
    // 您之前代码中喜欢的深灰背景 0xFF333333 可以作为 surface 或 surfaceContainer
    surface = Color(0xFF333333),
    onSurface = Color.White,
    background = Color(0xFF121212),
    onBackground = Color.White,
    surfaceContainer = Color(0xFF333333)
)

@Composable
fun AppTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System -> isSystemInDarkTheme()
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}