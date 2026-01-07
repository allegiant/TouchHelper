package org.eu.freex.tools.common.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
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