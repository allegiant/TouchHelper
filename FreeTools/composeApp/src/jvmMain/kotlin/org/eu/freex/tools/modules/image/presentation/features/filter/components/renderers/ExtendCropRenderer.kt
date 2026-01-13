package org.eu.freex.tools.modules.image.presentation.features.filter.components.renderers

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.eu.freex.tools.common.components.ModeSelectionRow
import org.eu.freex.tools.modules.image.domain.model.ExtendCropFilter
import org.eu.freex.tools.modules.image.domain.model.ImageFilter
import org.eu.freex.tools.modules.image.presentation.core.LocalImageViewModel

object ExtendCropRenderer: FilterRenderer {

    @Composable
    override fun Content(
        filter: ImageFilter,
        onFilterChange: (ImageFilter) -> Unit
    ) {
        val current = filter as? ExtendCropFilter?: return
        val viewModel = LocalImageViewModel.current
        val scope = rememberCoroutineScope()

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

            ModeSelectionRow(
                text = "两点确定矩形",
                description = "点击“取点”后，依次点击画面左上角和右下角。",
                selected = true,
                onClick = {}
            )

            // --- 1. 取点操作区 ---
            Button(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (current.status == 2) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                ),
                onClick = {
                    scope.launch {
                        // 1. 取第一个点
                        val p1 = viewModel.awaitPointPick() ?: return@launch
                        // 此时 step=1, 界面可提示“请点击右下角”
                        onFilterChange(current.copy(x1 = p1.x, y1 = p1.y, x2 = -1, y2 = -1, status = 1))

                        // 2. 取第二个点
                        val p2 = viewModel.awaitPointPick() ?: return@launch

                        // 3. 完成
                        // 自动修正：确保 x1<x2, y1<y2 (防止用户先点右下后点左上)
                        val minX = minOf(p1.x, p2.x)
                        val minY = minOf(p1.y, p2.y)
                        val maxX = maxOf(p1.x, p2.x)
                        val maxY = maxOf(p1.y, p2.y)

                        onFilterChange(current.copy(
                            x1 = minX, y1 = minY,
                            x2 = maxX, y2 = maxY,
                            status = 2
                        ))
                    }
                }
            ) {
                Icon(if (current.status == 2) Icons.Default.Crop else Icons.Default.TouchApp, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (current.status == 2) "重新取点" else "开始取点")
            }

            // --- 2. 坐标微调区 (仅在已选点后显示) ---
            if (current.status == 2) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    "坐标微调 (Pixel Perfect)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)
                )

                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    // 左列：起点
                    Column(modifier = Modifier.weight(1f)) {
                        CoordinateInput("左 (X1)", current.x1) { onFilterChange(current.copy(x1 = it)) }
                        Spacer(Modifier.height(8.dp))
                        CoordinateInput("上 (Y1)", current.y1) { onFilterChange(current.copy(y1 = it)) }
                    }

                    Spacer(Modifier.width(12.dp))

                    // 右列：终点
                    Column(modifier = Modifier.weight(1f)) {
                        CoordinateInput("右 (X2)", current.x2) { onFilterChange(current.copy(x2 = it)) }
                        Spacer(Modifier.height(8.dp))
                        CoordinateInput("下 (Y2)", current.y2) { onFilterChange(current.copy(y2 = it)) }
                    }
                }

                // 显示当前尺寸
                val w = current.x2 - current.x1
                val h = current.y2 - current.y1
                Text(
                    text = "当前尺寸: $w x $h px",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)
                )
            }
        }
    }

    /**
     * 带微调按钮的数字输入组件 (修正版)
     */
    @Composable
    private fun CoordinateInput(label: String, value: Int, onValueChange: (Int) -> Unit) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // 输入框
            OutlinedTextField(
                value = value.toString(),
                onValueChange = { str ->
                    // 只有输入纯数字时才更新
                    if (str.isEmpty()) return@OutlinedTextField
                    str.toIntOrNull()?.let { onValueChange(it) }
                },
                label = { Text(label, style = MaterialTheme.typography.bodySmall) },
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                // 【已删除】 contentPadding 参数，因为它导致了编译错误
            )

            // 微调按钮列
            Column(modifier = Modifier.padding(start = 4.dp)) {
                SmallIconButton(Icons.Default.Add) { onValueChange(value + 1) }
                SmallIconButton(Icons.Default.Remove) { onValueChange(value - 1) }
            }
        }
    }

    @Composable
    private fun SmallIconButton(icon: ImageVector, onClick: () -> Unit) {
        Surface(
            onClick = onClick,
            shape = MaterialTheme.shapes.extraSmall,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(20.dp).padding(1.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, modifier = Modifier.size(12.dp))
            }
        }
    }
}