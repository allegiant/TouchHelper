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
import org.eu.freex.tools.common.utils.toHexString
import org.eu.freex.tools.modules.image.presentation.viewmodel.model.PickRecord

@Composable
fun ColorRecordTable(
    records: List<PickRecord>,
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
            HeaderCell("颜色", 60.dp)
            HeaderCell("偏色", 60.dp)
            Spacer(Modifier.weight(1f))
            HeaderCell("操作", 30.dp)
        }

        HorizontalDivider()

        // 列表内容
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(records) { record ->
                RecordRow(record = record, onRemove = { onRemove(record.id) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun RecordRow(record: PickRecord, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp) // 紧凑行高
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 序号
        Text(
            text = "${record.index}",
            fontSize = 12.sp,
            modifier = Modifier.width(30.dp)
        )

        // 坐标
        Text(
            text = "${record.x},${record.y}",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(60.dp)
        )

        // 颜色 (块 + Hex)
        Row(modifier = Modifier.width(70.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(record.color)
                    .border(1.dp, Color.Gray)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = record.color.toHexString(false).uppercase(), // false = 不带 Alpha
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // 偏色 (暂时写死/展示)
        Text(
            text = record.offsetColor,
            fontSize = 11.sp,
            color = Color.Gray,
            modifier = Modifier.width(60.dp)
        )

        Spacer(Modifier.weight(1f))

        // 操作区
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = record.isChecked,
                onCheckedChange = {}, // TODO: 实现 Toggle 逻辑
                modifier = Modifier.size(24.dp),
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
            )
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