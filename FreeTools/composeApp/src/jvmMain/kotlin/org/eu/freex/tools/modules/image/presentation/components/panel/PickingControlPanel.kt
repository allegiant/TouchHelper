package org.eu.freex.tools.modules.image.presentation.components.panel

import org.eu.freex.tools.common.components.CompactNumericInput
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.common.components.CompactNumericInput
import org.eu.freex.tools.modules.image.presentation.components.panel.sections.BinarizationPreview
import org.eu.freex.tools.modules.image.presentation.components.panel.sections.ColorRecordTable
import org.eu.freex.tools.modules.image.presentation.viewmodel.FindDirection
import org.eu.freex.tools.modules.image.presentation.viewmodel.PickingToolViewModel

@Composable
fun PickingControlPanel(
    viewModel: PickingToolViewModel,
    onGenerateCode: (String) -> Unit, // [修改] 回调带回生成的代码
    modifier: Modifier = Modifier
) {
    // === 1. 收集状态 ===
    val binaryResult by viewModel.binaryResultState.collectAsState()
    val previewState by viewModel.previewState.collectAsState()
    val featurePoints by viewModel.featurePoints.collectAsState()

    // 新增配置状态
    val similarity by viewModel.globalSimilarity.collectAsState()
    val direction by viewModel.searchDirection.collectAsState()

    // UI 本地状态
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("多点找色","区域找色", "区域找图")

    Column(
        modifier = modifier
            .width(320.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // === 2. 顶部预览区 ===
        BinarizationPreview(
            binaryBitmap = binaryResult,
            modifier = Modifier.height(200.dp)
        )
        HorizontalDivider()

        // === 3. 中间 Tab 区 ===
        Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            Box(modifier = Modifier.weight(1f).padding(8.dp)) {
                when(selectedTabIndex) {
                    0 -> ConfigTabContent(
                        similarity = similarity,
                        onSimilarityChange = viewModel::updateSimilarity,
                        direction = direction,
                        onDirectionChange = viewModel::updateDirection
                    )
                    1 -> Text("图片列表已移至顶部标签页管理")
                    2 -> Text("OCR 功能开发中...")
                }
            }
        }

        HorizontalDivider()
        // === 4. 查找测试
        Column(modifier = Modifier.height(150.dp).padding(8.dp).fillMaxWidth()) {
            Text("查找测试", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "结果 -1,-1",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
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
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("开始测试")
                }
            }
        }

        HorizontalDivider()
        // === 4. 底部记录表 ===
        Column(modifier = Modifier.height(200.dp).fillMaxWidth()) {
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
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("复制完整脚本")
                }
            }
        }
    }
}

// [新增] 配置页组件
@Composable
private fun ConfigTabContent(
    similarity: Float,
    onSimilarityChange: (Float) -> Unit,
    direction: FindDirection,
    onDirectionChange: (FindDirection) -> Unit
) {
    Column {
        Spacer(Modifier.height(8.dp))

        // 1. 相似度滑块
        Text("相似度: ${"%.2f".format(similarity)}", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = similarity,
            onValueChange = onSimilarityChange,
            valueRange = 0.5f..1.0f,
            steps = 50 // 0.01 一档
        )

        Spacer(Modifier.height(16.dp))

        // 2. 方向选择
        Text("查找方向", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))

        var expanded by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                .clickable { expanded = true }
                .padding(12.dp)
        ) {
            Text(direction.label, style = MaterialTheme.typography.bodyMedium)
            Icon(Icons.Default.ArrowDropDown, "", Modifier.align(Alignment.CenterEnd))

            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                FindDirection.entries.forEach { dir ->
                    DropdownMenuItem(
                        text = { Text(dir.label) },
                        onClick = {
                            onDirectionChange(dir)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}