package org.eu.freex.tools.modules.image.presentation.components.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.common.components.ColorRuleListPanel
import org.eu.freex.tools.common.model.ColorRule

@Composable
fun MultiColorRuleEditor(
    isInvert: Boolean,
    keepOriginal: Boolean,
    rules: List<ColorRule>,
    onInvertChange: (Boolean) -> Unit,
    onKeepOriginalChange: (Boolean) -> Unit,
    onRulesChange: (List<ColorRule>) -> Unit,
    onRequestPickColor: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            FilterCheckbox("背景色 (反色)", isInvert, onInvertChange)
            FilterCheckbox("颜色选留 (原色)", keepOriginal, onKeepOriginalChange)
        }

        ColorRuleListPanel(
            rules = rules,
            onRulesChange = onRulesChange,
            onRequestPickColor = onRequestPickColor
        )
    }
}

@Composable
private fun FilterCheckbox(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { onCheckedChange(!checked) }
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.8f).size(32.dp)
        )
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}