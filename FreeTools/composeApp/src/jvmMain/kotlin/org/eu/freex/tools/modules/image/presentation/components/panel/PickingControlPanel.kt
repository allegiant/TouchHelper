package org.eu.freex.tools.modules.image.presentation.components.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.eu.freex.tools.modules.image.presentation.components.panel.sections.BinarizationPreview
import org.eu.freex.tools.modules.image.presentation.components.panel.sections.ColorRecordTable
import org.eu.freex.tools.modules.image.presentation.viewmodel.FindDirection
import org.eu.freex.tools.modules.image.presentation.viewmodel.PickingToolViewModel

@Composable
fun PickingControlPanel(
    viewModel: PickingToolViewModel,
    onGenerateCode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // === 1. 收集状态 ===
    val binaryResult by viewModel.binaryResultState.collectAsState()
    val featurePoints by viewModel.featurePoints.collectAsState()

    // 配置状态
    val similarity by viewModel.globalSimilarity.collectAsState()
    val direction by viewModel.searchDirection.collectAsState()

    // UI 本地状态
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("多点找色", "区域找色", "区域找图")

    Column(
        modifier = modifier
            .width(320.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // === 2. 顶部预览区 (已恢复) ===
        // 提示：如果要去掉里面的文字标题，请修改 BinarizationPreview.kt 内部代码
        // 这里我们将高度从 200dp 压缩到 140dp，节省空间
        BinarizationPreview(
            binaryBitmap = binaryResult,
            modifier = Modifier.height(140.dp).fillMaxWidth()
        )

        HorizontalDivider()

        // === 3. 中间 Tab 区 ===
        Column(modifier = Modifier.fillMaxWidth()) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.height(36.dp) // 更紧凑的 Tab 栏
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title, style = MaterialTheme.typography.labelMedium) },
                        modifier = Modifier.height(36.dp)
                    )
                }
            }

            Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                when (selectedTabIndex) {
                    0 -> ConfigTabContent(
                        similarity = similarity,
                        onSimilarityChange = viewModel::updateSimilarity,
                        direction = direction,
                        onDirectionChange = viewModel::updateDirection
                    )
                    1 -> Text("列表已移至顶部标签", style = MaterialTheme.typography.bodySmall)
                    2 -> Text("OCR 开发中...", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        HorizontalDivider()

        // === 4. 查找测试区 (紧凑版) ===
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("查找测试", style = MaterialTheme.typography.labelMedium)
                // 结果直接显示在标题旁边
                Text(
                    text = "结果: -1, -1",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = {
                    val code = viewModel.generateScript()
                    onGenerateCode(code)
                },
                modifier = Modifier.fillMaxWidth().height(32.dp), // 按钮高度更小
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("运行测试", fontSize = 12.sp)
            }
        }

        HorizontalDivider()

        // === 5. 底部记录表 (自动占据剩余空间) ===
        Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
            ColorRecordTable(
                records = featurePoints,
                onUpdate = { point -> viewModel.updatePoint(point) },
                onRemove = { id -> viewModel.removePointById(id) },
                modifier = Modifier.weight(1f)
            )

            // 底部按钮
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = {
                        val code = viewModel.generateScript()
                        onGenerateCode(code)
                    },
                    modifier = Modifier.fillMaxWidth().height(32.dp),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("复制脚本", fontSize = 12.sp)
                }
            }
        }
    }
}

// [修改] 紧凑型配置页组件：使用 BasicTextField 实现 40dp 高度
@Composable
private fun ConfigTabContent(
    similarity: Float,
    onSimilarityChange: (Float) -> Unit,
    direction: FindDirection,
    onDirectionChange: (FindDirection) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // === 左侧：相似度 ===
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "相似度",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(2.dp))

            var textValue by remember(similarity) { mutableStateOf(similarity.toString()) }

            // 自定义紧凑输入框
            BasicTextField(
                value = textValue,
                onValueChange = { str ->
                    textValue = str
                    str.toFloatOrNull()?.let { value ->
                        if (value in 0.0f..1.0f) {
                            onSimilarityChange(value)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp) // 降低到 36dp
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp),
                textStyle = TextStyle(
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        innerTextField()
                    }
                }
            )
        }

        // === 右侧：查找方向 ===
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "查找方向",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(2.dp))

            var expanded by remember { mutableStateOf(false) }

            // 紧凑下拉框
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp) // 与输入框高度一致
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(direction.label, style = TextStyle(fontSize = 12.sp))
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    FindDirection.entries.forEach { dir ->
                        DropdownMenuItem(
                            text = { Text(dir.label, fontSize = 12.sp) },
                            onClick = {
                                onDirectionChange(dir)
                                expanded = false
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}