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
    // 收集 ViewModel 的状态
    val binaryResult by viewModel.binaryResultState.collectAsState()
    val previewState by viewModel.previewState.collectAsState()
    val records by viewModel.pickedRecords.collectAsState()

    // UI 本地状态 (Tab 切换)
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("多点找色", "区域找图", "字库")

    Column(
        modifier = modifier
            .width(320.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // === 1. 顶部预览区 (放大镜) ===
        // 占据顶部 200dp
        BinarizationPreview(
            binaryBitmap = binaryResult,
            modifier = Modifier.height(200.dp)
        )
        HorizontalDivider()

        // === 2. 中间功能 Tab 区 ===
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

            // Tab 内容区 (配置参数)
            Box(modifier = Modifier.weight(1f).padding(8.dp)) {
                when(selectedTabIndex) {
                    0 -> Text("这里放 [相似度] [查找方向] [偏色] 等滑块配置") // TODO: 下一步实现 Slide 配置
                    1 -> Text("这里放图片选择列表")
                    2 -> Text("OCR 字典配置")
                }
            }
        }

        HorizontalDivider()

        // === 3. 底部记录表 ===
        // 占据底部 250dp (包括生成按钮)
        Column(modifier = Modifier.height(250.dp).fillMaxWidth()) {
            // 列表
            ColorRecordTable(
                records = records,
                onRemove = { id -> viewModel.removeRecord(id) },
                modifier = Modifier.weight(1f)
            )

            // 底部按钮栏
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