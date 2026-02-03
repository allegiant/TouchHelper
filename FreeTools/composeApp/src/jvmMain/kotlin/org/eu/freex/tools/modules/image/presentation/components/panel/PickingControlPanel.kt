package org.eu.freex.tools.modules.image.presentation.components.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.modules.image.presentation.components.panel.sections.BinarizationPreview
import org.eu.freex.tools.modules.image.presentation.components.panel.sections.ColorRecordTable
import org.eu.freex.tools.modules.image.presentation.viewmodel.PickingToolViewModel

@Composable
fun PickingControlPanel(
    viewModel: PickingToolViewModel,
    onGenerateCode: () -> Unit,
    modifier: Modifier = Modifier
) {
    // === 1. 收集状态 ===
    val binaryResult by viewModel.binaryResultState.collectAsState()
    val previewState by viewModel.previewState.collectAsState()

    // [修改] 监听 featurePoints 而不是 pickedRecords
    val featurePoints by viewModel.featurePoints.collectAsState()

    // UI 本地状态
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("多点找色", "区域找图", "字库")

    Column(
        modifier = modifier
            .width(320.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // === 2. 顶部预览区 ===
        // 这里可以根据需求决定显示 BinarizationPreview 还是 放大镜
        // 目前你的设计是显示二值化结果
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
                    0 -> Text("这里放 [相似度] [查找方向] [偏色] 等配置")
                    1 -> Text("图片列表")
                    2 -> Text("OCR 配置")
                }
            }
        }

        HorizontalDivider()

        // === 4. 底部记录表 (使用 FeaturePoint) ===
        Column(modifier = Modifier.height(250.dp).fillMaxWidth()) {
            ColorRecordTable(
                records = featurePoints,
                // [新增] 勾选/反选时更新 ViewModel
                onUpdate = { point -> viewModel.updatePoint(point) },
                // [修改] 删除时调用 removePointById
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
                    onClick = onGenerateCode,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("复制完整脚本")
                }
            }
        }
    }
}