package org.eu.freex.tools.modules.image.presentation.features.filter.components.renderers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.eu.freex.tools.common.ColorRule
import org.eu.freex.tools.modules.image.domain.model.ImageFilter
import org.eu.freex.tools.modules.image.domain.model.MultiColorFilter
// 需要确保你能获取到 ImageViewModel，推荐通过 CompositionLocal 或者修改 Content 接口
// 假设这里我们通过 CompositionLocal 获取 (见步骤8说明)
import org.eu.freex.tools.modules.image.presentation.core.LocalImageViewModel

object MultiColorRenderer : FilterRenderer {

    @Composable
    override fun Content(filter: ImageFilter, onFilterChange: (ImageFilter) -> Unit) {
        val currentFilter = filter as? MultiColorFilter ?: return
        val viewModel = LocalImageViewModel.current // 获取 ViewModel

        fun updateRules(newRules: List<ColorRule>) {
            onFilterChange(currentFilter.copy(rules = newRules))
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

            // --- 全局选项 ---
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                FilterCheckbox("背景色 (反色)", currentFilter.isInvert) { onFilterChange(currentFilter.copy(isInvert = it)) }
                FilterCheckbox("颜色选留 (原色)", currentFilter.keepOriginal) { onFilterChange(currentFilter.copy(keepOriginal = it)) }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text("颜色列表 (${currentFilter.rules.size})", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

            // --- 规则列表 ---
            currentFilter.rules.forEachIndexed { index, rule ->
                ColorRuleRow(
                    index = index + 1,
                    rule = rule,
                    onUpdate = { updated ->
                        val newRules = currentFilter.rules.toMutableList()
                        newRules[index] = updated
                        updateRules(newRules)
                    },
                    onDelete = {
                        val newRules = currentFilter.rules.toMutableList()
                        newRules.removeAt(index)
                        updateRules(newRules)
                    },
                    onPickColor = {
                        // 启动取色
                        viewModel.startColorPick{ pickedColor ->
                            val hex = "#%02X%02X%02X".format(
                                (pickedColor.red * 255).toInt(),
                                (pickedColor.green * 255).toInt(),
                                (pickedColor.blue * 255).toInt()
                            )
                            val newRule = rule.copy(targetHex = hex)
                            val newRules = currentFilter.rules.toMutableList()
                            newRules[index] = newRule
                            updateRules(newRules)
                        }
                    }
                )
                if (index < currentFilter.rules.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), thickness = 1.dp)
                }
            }

            // --- 添加按钮 ---
            Button(
                onClick = {
                    val newId = (currentFilter.rules.maxOfOrNull { it.id } ?: 0) + 1
                    val newRule = ColorRule(id = newId, targetHex = "FF0000", biasHex = "101010", isEnabled = true)
                    updateRules(currentFilter.rules + newRule)
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("添加颜色规则")
            }
        }
    }

    @Composable
    private fun ColorRuleRow(
        index: Int,
        rule: ColorRule,
        onUpdate: (ColorRule) -> Unit,
        onDelete: () -> Unit,
        onPickColor: () -> Unit
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            // 启用 Checkbox
            Checkbox(
                checked = rule.isEnabled,
                onCheckedChange = { onUpdate(rule.copy(isEnabled = it)) },
                modifier = Modifier.scale(0.8f).size(32.dp)
            )

            Text("$index", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(16.dp))

            // 颜色预览
            val previewColor = remember(rule.targetHex) { parseColorSafe(rule.targetHex) }
            Box(
                modifier = Modifier.size(24.dp).background(previewColor, RoundedCornerShape(4.dp)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
            )

            // 吸管按钮
            IconButton(onClick = onPickColor, modifier = Modifier.size(28.dp).padding(start = 4.dp)) {
                Icon(Icons.Default.Colorize, contentDescription = "Pick", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            }

            Spacer(Modifier.width(4.dp))

            Column(modifier = Modifier.weight(1f)) {
                CompactHexInput("色:", rule.targetHex) { onUpdate(rule.copy(targetHex = it)) }
                CompactHexInput("偏:", rule.biasHex) { onUpdate(rule.copy(biasHex = it)) }
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Del", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            }
        }
    }

    @Composable
    private fun CompactHexInput(label: String, value: String, onValueChange: (String) -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = Color.Gray)
            Spacer(Modifier.width(4.dp))
            BasicTextField(
                value = value,
                onValueChange = { if (it.length <= 7) onValueChange(it) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface),
                singleLine = true,
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(2.dp)).padding(horizontal = 4.dp, vertical = 2.dp).fillMaxWidth()
            )
        }
    }

    @Composable
    private fun FilterCheckbox(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onCheckedChange(!checked) }) {
            Checkbox(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.scale(0.8f).size(32.dp))
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }

    private fun parseColorSafe(hex: String): Color {
        return try {
            val cleanHex = hex.replace("#", "")
            if (cleanHex.length == 6) {
                Color(cleanHex.substring(0, 2).toInt(16), cleanHex.substring(2, 4).toInt(16), cleanHex.substring(4, 6).toInt(16))
            } else Color.Transparent
        } catch (e: Exception) { Color.Transparent }
    }
}