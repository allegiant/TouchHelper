package org.eu.freex.tools.common.i18n

import androidx.compose.runtime.*
import org.eu.freex.tools.common.AppStrings
import org.eu.freex.tools.common.ZhStrings

val LocalStrings = staticCompositionLocalOf { ZhStrings } // 默认中文

@Composable
fun ProvideAppStrings(
    strings: AppStrings,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalStrings provides strings, content = content)
}