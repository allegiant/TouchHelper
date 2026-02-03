package org.eu.freex.tools.modules.image.presentation.features.filter.components.renderers

// [新增] 引入新架构组件
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.eu.freex.tools.common.components.ModeSelectionRow
import org.eu.freex.tools.common.model.PickingToolState
import org.eu.freex.tools.modules.image.domain.model.ExtendCropFilter
import org.eu.freex.tools.modules.image.domain.model.ImageFilter
import org.eu.freex.tools.modules.image.presentation.viewmodel.ImageWorkbenchViewModel
import org.koin.compose.koinInject

object ExtendCropRenderer : FilterRenderer {

    @Composable
    override fun Content(
        filter: ImageFilter,
        onFilterChange: (ImageFilter) -> Unit
    ) {
        val current = filter as? ExtendCropFilter ?: return

        // [修改] 注入 ViewModel
        val workbenchViewModel: ImageWorkbenchViewModel = koinInject()

        // [新增] 本地状态机：0=闲置, 1=等待第1个点, 2=等待第2个点
        var pickingStage by remember { mutableStateOf(0) }

        // [新增] 保持最新状态引用，供 LaunchedEffect 使用 (避免闭包过期)
        val currentFilterState by rememberUpdatedState(current)
        val onFilterChangeState by rememberUpdatedState(onFilterChange)

        // [新增] 监听取点事件流
        LaunchedEffect(Unit) {
            workbenchViewModel.pickEvent.collect { event ->
                if (event is IntOffset) {
                    when (pickingStage) {
                        1 -> {
                            // 收到第1个点
                            val p1 = event
                            // 更新 Filter 状态 (status=1)
                            onFilterChangeState(
                                currentFilterState.copy(
                                    x1 = p1.x, y1 = p1.y,
                                    x2 = -1, y2 = -1,
                                    status = 1
                                )
                            )
                            // 进入下一阶段，并再次请求取点
                            pickingStage = 2
                            workbenchViewModel.activeTool(PickingToolState.PointPicker)
                        }

                        2 -> {
                            // 收到第2个点
                            val p2 = event
                            val p1x = currentFilterState.x1
                            val p1y = currentFilterState.y1

                            // 自动修正坐标 (Min/Max)
                            val minX = minOf(p1x, p2.x)
                            val minY = minOf(p1y, p2.y)
                            val maxX = maxOf(p1x, p2.x)
                            val maxY = maxOf(p1y, p2.y)

                            // 更新最终结果 (status=2)
                            onFilterChangeState(
                                currentFilterState.copy(
                                    x1 = minX, y1 = minY,
                                    x2 = maxX, y2 = maxY,
                                    status = 2
                                )
                            )
                            // 结束流程
                            pickingStage = 0
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

            ModeSelectionRow(
                text = "两点确定矩形",
                description = if (pickingStage == 2) "请点击画面右下角..." else "点击“取点”后，依次点击画面左上角和右下角。",
                selected = true,
                onClick = {}
            )

            // --- 1. 取点操作区 ---
            Button(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                colors = ButtonDefaults.buttonColors(
                    // 根据状态改变颜色：完成(2)用Tertiary，正在取点(1/2)或闲置(0)用Primary
                    containerColor = if (current.status == 2) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                ),
                onClick = {
                    // [修改] 启动取点流程
                    pickingStage = 1
                    workbenchViewModel.activeTool(PickingToolState.PointPicker)
                }
            ) {
                Icon(if (current.status == 2) Icons.Default.Crop else Icons.Default.TouchApp, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                // 根据本地 pickingStage 显示提示，比依赖 filter status 更即时
                val buttonText = when {
                    pickingStage == 1 -> "请点击左上角"
                    pickingStage == 2 -> "请点击右下角"
                    current.status == 2 -> "重新取点"
                    else -> "开始取点"
                }
                Text(buttonText)
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
     * 带微调按钮的数字输入组件 (保留原样)
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
                    if (str.isEmpty()) return@OutlinedTextField
                    str.toIntOrNull()?.let { onValueChange(it) }
                },
                label = { Text(label, style = MaterialTheme.typography.bodySmall) },
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
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