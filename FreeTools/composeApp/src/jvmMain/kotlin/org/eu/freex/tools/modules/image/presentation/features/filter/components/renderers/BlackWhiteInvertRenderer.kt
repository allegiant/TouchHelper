package org.eu.freex.tools.modules.image.presentation.features.filter.components.renderers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.common.components.HelpTooltip
import org.eu.freex.tools.modules.image.domain.model.BlackWhiteInvertFilter
import org.eu.freex.tools.modules.image.domain.model.ImageFilter

object BlackWhiteInvertRenderer : FilterRenderer {

    // 定义常量对应 Rust 端的逻辑
    private const val MODE_AUTO_TO_WHITE_BG = 0
    private const val MODE_AUTO_TO_BLACK_BG = 1
    private const val MODE_FORCE = 2

    @Composable
    override fun Content(
        filter: ImageFilter,
        onFilterChange: (ImageFilter) -> Unit
    ) {
        val currentFilter = filter as? BlackWhiteInvertFilter ?: return

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

            // --- 选项 1: 统一为白底黑字 (推荐) ---
            ModeSelectionRow(
                text = "统一为白底黑字 (推荐)",
                description = "智能识别背景。如果图片是“黑底”，则自动反转为“白底”。\n适用于OCR识别（引擎通常更喜欢白底）。",
                selected = currentFilter.mode == MODE_AUTO_TO_WHITE_BG,
                onClick = { onFilterChange(currentFilter.copy(mode = MODE_AUTO_TO_WHITE_BG)) }
            )

            // --- 选项 2: 统一为黑底白字 ---
            ModeSelectionRow(
                text = "统一为黑底白字",
                description = "智能识别背景。如果图片是“白底”，则自动反转为“黑底”。\n适用于二值化找色或特殊字库制作。",
                selected = currentFilter.mode == MODE_AUTO_TO_BLACK_BG,
                onClick = { onFilterChange(currentFilter.copy(mode = MODE_AUTO_TO_BLACK_BG)) }
            )

            // --- 选项 3: 强制反色 ---
            ModeSelectionRow(
                text = "强制反色",
                description = "不进行智能判断，直接将黑色变白，白色变黑。",
                selected = currentFilter.mode == MODE_FORCE,
                onClick = { onFilterChange(currentFilter.copy(mode = MODE_FORCE)) }
            )
        }
    }

    @Composable
    private fun ModeSelectionRow(
        text: String,
        description: String,
        selected: Boolean,
        onClick: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .selectable(
                    selected = selected,
                    onClick = onClick,
                    role = Role.RadioButton
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = null, // onClick handled by Row
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = MaterialTheme.colorScheme.outline
                )
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp)
            )

            if (description.isNotEmpty()) {
                HelpTooltip(description = description)
            }
        }
    }
}