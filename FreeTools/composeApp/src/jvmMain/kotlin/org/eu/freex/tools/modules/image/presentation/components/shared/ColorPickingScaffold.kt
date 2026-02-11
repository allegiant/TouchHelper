package org.eu.freex.tools.modules.image.presentation.components.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

@Composable
fun ColorPickingScaffold(
    modifier: Modifier = Modifier,
    leftPanel: (@Composable BoxScope.() -> Unit)? = null,
    centerPanel: @Composable BoxScope.() -> Unit,
    rightPanel: (@Composable BoxScope.() -> Unit)? = null,
    leftPanelWidth: Dp = Dp.Unspecified,
    rightPanelWidth: Dp = Dp.Unspecified,
    horizontalGap: Dp = Dp.Unspecified
) {
    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = if (horizontalGap == Dp.Unspecified) {
            Arrangement.Start
        } else {
            Arrangement.spacedBy(horizontalGap)
        }
    ) {
        if (leftPanel != null) {
            val leftModifier = if (leftPanelWidth == Dp.Unspecified) {
                Modifier.wrapContentWidth().fillMaxHeight()
            } else {
                Modifier.width(leftPanelWidth).fillMaxHeight()
            }
            Box(modifier = leftModifier, content = leftPanel)
        }

        Box(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            content = centerPanel
        )

        if (rightPanel != null) {
            val rightModifier = if (rightPanelWidth == Dp.Unspecified) {
                Modifier.wrapContentWidth().fillMaxHeight()
            } else {
                Modifier.width(rightPanelWidth).fillMaxHeight()
            }
            Box(modifier = rightModifier, content = rightPanel)
        }
    }
}