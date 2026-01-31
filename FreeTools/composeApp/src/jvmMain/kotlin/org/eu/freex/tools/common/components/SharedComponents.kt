package org.eu.freex.tools.common.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * checkbox开关组件
 */
@Composable
fun FCheckBox(
    modifier: Modifier = Modifier,
    text: String,
    isEnabled: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable { onChange(!isEnabled) }
    ) {
        Checkbox(
            checked = isEnabled,
            onCheckedChange = onChange,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                uncheckedColor = MaterialTheme.colorScheme.outline
            )
        )
        Text(
            text,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

// ==========================================
// 1. UInt 版本 (整数) - 新增 onlyOdd 参数
// ==========================================
@Composable
fun CompactNumericInput(
    label: String,
    value: UInt?,
    onValueChange: (UInt?) -> Unit,
    unit: String = "",
    // 【新增】仅允许奇数开关
    onlyOdd: Boolean = false
) {
    val intRegex = Regex("^\\d*$")

    // 判断当前值是否违规（不仅要是偶数，还得是非空才算错，空值不算错）
    val isEvenError = if (onlyOdd && value != null) {
        (value % 2u == 0u) // 如果是偶数，则标记错误
    } else {
        false
    }

    CompactNumericInputBase(
        label = label,
        valueStr = value?.toString() ?: "",
        onValueStrChange = { newStr ->
            val newInt = newStr.toUIntOrNull()
            if (newStr.isEmpty()) {
                onValueChange(null)
            } else if (newInt != null) {
                onValueChange(newInt)
            }
        },
        validationRegex = intRegex,
        keyboardType = KeyboardType.Number,
        unit = unit,
        // 【新增】传入错误状态
        isError = isEvenError
    )
}

// ==========================================
// 2. Float 版本 (小数) - 保持不变
// ==========================================
@Composable
fun CompactNumericInput(
    label: String,
    value: Double?,
    onValueChange: (Double?) -> Unit,
    unit: String = ""
) {
    val floatRegex = Regex("^\\d*\\.?\\d*$")

    CompactNumericInputBase(
        label = label,
        valueStr = value?.toString() ?: "",
        onValueStrChange = { newStr ->
            val newFloat = newStr.toDoubleOrNull()
            if (newStr.isEmpty()) {
                onValueChange(null)
            } else if (newFloat != null) {
                onValueChange(newFloat)
            }
        },
        validationRegex = floatRegex,
        keyboardType = KeyboardType.Decimal,
        unit = unit,
        isError = false // 小数版本暂时没有错误状态需求
    )
}

// ==========================================
// 3. 基础实现 - 新增 isError 样式处理
// ==========================================
@Composable
private fun CompactNumericInputBase(
    label: String,
    valueStr: String,
    onValueStrChange: (String) -> Unit,
    validationRegex: Regex,
    keyboardType: KeyboardType,
    unit: String,
    // 【新增】错误状态标志
    isError: Boolean
) {
    var textState by remember { mutableStateOf(valueStr) }

    LaunchedEffect(valueStr) {
        val isFloatEquivalent = valueStr.toFloatOrNull() == textState.toFloatOrNull()
        val isIntEquivalent = valueStr.toUIntOrNull() == textState.toUIntOrNull()
        if (!isFloatEquivalent && !isIntEquivalent) {
            textState = valueStr
        }
    }

    // 【新增】决定文字颜色：如果是 Error 状态，显示红色
    val textColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    // 边框颜色也可以随之变红（可选）
    val borderColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(80.dp)
        )

        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            // 使用动态边框色
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier
                .height(32.dp)
                .weight(1f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                BasicTextField(
                    value = textState,
                    onValueChange = { newValue ->
                        if (newValue.matches(validationRegex)) {
                            textState = newValue
                            onValueStrChange(newValue)
                        }
                    },
                    // 应用动态颜色
                    textStyle = LocalTextStyle.current.copy(
                        color = textColor,
                        fontSize = 14.sp
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    singleLine = true,
                    cursorBrush = SolidColor(if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f)
                )

                if (unit.isNotEmpty()) {
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 带说明图标的单选行
 */
@Composable
fun ModeSelectionRow(
    text: String,
    description: String, // 新增说明参数
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
            onClick = null,
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

        // 【新增】帮助图标和 Tooltip
        if (description.isNotEmpty()) {
            HelpTooltip(description = description)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HelpTooltip(description: String) {
    TooltipArea(
        tooltip = {
            Box(
                modifier = Modifier
                    .shadow(4.dp, RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(4.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                    .padding(12.dp) // 稍微增加 padding 让排版更舒服
                    .fillMaxWidth(0.7f) // 稍微加宽一点以容纳列表
            ) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.2 // 增加行高提高可读性
                )
            }
        },
        delayMillis = 300
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
            contentDescription = "说明",
            modifier = Modifier
                .padding(start = 8.dp)
                .size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun HelpTooltip(
    text: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var showPopup by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        androidx.compose.material.Text(text, style = MaterialTheme.typography.titleSmall)

        Box(modifier = Modifier.padding(start = 4.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                contentDescription = "帮助",
                modifier = Modifier
                    .size(14.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null // 移除点击水波纹避免干扰文字
                    ) { showPopup = true },
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (showPopup) {
                androidx.compose.ui.window.Popup(
                    alignment = Alignment.TopCenter,
                    // 这里的 Offset 可以根据实际 UI 表现微调
                    // x=160 使其向右偏移出侧边栏，y=30 向下偏移避开手指
                    offset = IntOffset(x = 160, y = 30),
                    onDismissRequest = { showPopup = false }
                ) {
                    Surface(
                        modifier = Modifier.width(320.dp), // 强制固定宽度，防止内容挤乱
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            content()
                        }
                    }
                }
            }
        }
    }
}