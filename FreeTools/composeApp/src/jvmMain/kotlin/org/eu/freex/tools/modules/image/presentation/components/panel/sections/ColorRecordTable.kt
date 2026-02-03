package org.eu.freex.tools.modules.image.presentation.components.panel.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.eu.freex.tools.modules.image.domain.model.FeaturePoint

@Composable
fun ColorRecordTable(
    // [修改] 数据源改为 FeaturePoint
    records: List<FeaturePoint>,
    // [新增] 更新回调 (用于 Checkbox 勾选)
    onUpdate: (FeaturePoint) -> Unit,
    // [修改] 删除回调 (使用 ID 或 Index，这里 FeaturePoint 有 ID)
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // 表头
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(vertical = 4.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderCell("序号", 30.dp)
            HeaderCell("坐标", 60.dp)
            HeaderCell("颜色", 70.dp)
            HeaderCell("偏色", 50.dp)
            Spacer(Modifier.weight(1f))
            HeaderCell("操作", 40.dp) // 稍微宽一点容纳 Checkbox 和 Delete
        }

        HorizontalDivider()

        // 列表内容
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(records, key = { it.id }) { point ->
                RecordRow(
                    point = point,
                    onUpdate = onUpdate,
                    onRemove = { onRemove(point.id) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun RecordRow(
    point: FeaturePoint,
    onUpdate: (FeaturePoint) -> Unit,
    onRemove: () -> Unit
) {
    // 解析颜色用于显示 (FeaturePoint 存的是 Hex String)
    val displayColor = try {
        val hex = point.colorHex.removePrefix("#")
        val alpha = if (hex.length == 8) hex.substring(0, 2) else "FF"
        val rgb = if (hex.length == 8) hex.substring(2) else hex
        val fullHex = "$alpha$rgb"
        Color(fullHex.toLong(16))
    } catch (e: Exception) {
        Color.Gray
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. 序号
        Text(
            text = "${point.index}",
            fontSize = 12.sp,
            modifier = Modifier.width(30.dp)
        )

        // 2. 坐标
        Text(
            text = "${point.x},${point.y}",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(60.dp)
        )

        // 3. 颜色 (块 + Hex)
        Row(modifier = Modifier.width(70.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(displayColor)
                    .border(1.dp, Color.Gray)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = point.colorHex.uppercase().takeLast(6), // 只显示后6位，简洁点
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // 4. 偏色 (Tolerance)
        // 这里暂时只显示文本，点击可以弹出修改框(未来实现)
        Text(
            text = point.tolerance,
            fontSize = 11.sp,
            color = Color.Gray,
            modifier = Modifier
                .width(50.dp)
            // .clickable { /* TODO: 弹出偏色编辑框 */ }
        )

        Spacer(Modifier.weight(1f))

        // 5. 操作区
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 勾选框
            Checkbox(
                checked = point.isChecked,
                onCheckedChange = { isChecked ->
                    onUpdate(point.copy(isChecked = isChecked))
                },
                modifier = Modifier.size(24.dp),
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
            )

            Spacer(Modifier.width(4.dp))

            // 删除按钮
            IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Delete, "删除", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, width: androidx.compose.ui.unit.Dp) {
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(width))
}