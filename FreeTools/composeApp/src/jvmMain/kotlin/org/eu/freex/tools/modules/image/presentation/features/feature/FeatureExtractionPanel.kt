package org.eu.freex.tools.modules.image.presentation.features.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.eu.freex.tools.common.model.PickEvent
import org.eu.freex.tools.common.model.PickingToolState
import org.eu.freex.tools.modules.image.domain.model.FeaturePoint
import org.eu.freex.tools.modules.image.presentation.viewmodel.EditorCanvasViewModel

// 查找方向枚举
enum class FindDirection(val label: String, val code: Int) {
    LEFT_TOP_RIGHT_BOTTOM("左上->右下", 1),
    CENTER_OUT("中心->四周", 2),
    RIGHT_TOP_LEFT_BOTTOM("右上->左下", 3)
}

// 找色模式枚举
enum class ColorSearchMode(val label: String) {
    MULTI_POINT("多点找色"),
    AREA("区域找色")
}

@Composable
fun FeatureExtractionPanel(
    modifier: Modifier = Modifier,
    viewModel: EditorCanvasViewModel,
    onStartRegionSelect: () -> Unit // 回调：触发画布框选模式
) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    // UI 状态
    var selectedTabIndex by remember { mutableStateOf(0) } // 0:找色, 1:找图, 2:元素
    var colorSearchMode by remember { mutableStateOf(ColorSearchMode.MULTI_POINT) }
    var similarity by remember { mutableStateOf(0.9f) }
    var direction by remember { mutableStateOf(FindDirection.LEFT_TOP_RIGHT_BOTTOM) }
    var expandDirectionMenu by remember { mutableStateOf(false) }

    var activePickingIndex by remember { mutableStateOf<Int?>(null) }

    // [Event Listener] Listens for the broadcast
    LaunchedEffect(Unit) {
        viewModel.pickEvent.collect { event ->
            val index = activePickingIndex
            if (index != null && event is PickEvent.ColorPicked) {
                viewModel.addFeaturePoint(event.x, event.y, event.color)

                viewModel.setActiveTool(PickingToolState.ColorPicker)
                activePickingIndex = null
            }
        }
    }

    Column(modifier = modifier.fillMaxHeight().padding(4.dp)) {

        // 1. 顶部主 Tab
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.height(48.dp)
        ) {
            Tab(selected = selectedTabIndex == 0, onClick = { selectedTabIndex = 0 }, text = { Text("找色") })
            Tab(selected = selectedTabIndex == 1, onClick = { selectedTabIndex = 1 }, text = { Text("找图") })
            Tab(selected = selectedTabIndex == 2, onClick = { selectedTabIndex = 2 }, text = { Text("元素") })
        }

        Spacer(modifier = Modifier.height(8.dp))

        // [找色模式下] 显示子模式切换 (多点 vs 区域)
        if (selectedTabIndex == 0) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ColorSearchMode.values().forEach { mode ->
                    FilterChip(
                        selected = colorSearchMode == mode,
                        onClick = { colorSearchMode = mode },
                        label = { Text(mode.label, fontSize = 12.sp) },
                        modifier = Modifier.weight(1f).height(32.dp)
                    )
                }
            }
        }

        if (selectedTabIndex == 0 && colorSearchMode == ColorSearchMode.AREA) {
            RegionConfigSection(
                region = uiState.searchRegion,
                onSelectRegion = onStartRegionSelect,
                onClearRegion = { viewModel.clearSearchRegion() }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 2. 列表表头
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("序号", modifier = Modifier.weight(0.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("坐标", modifier = Modifier.weight(1.5f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("颜色", modifier = Modifier.weight(1.5f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("偏色", modifier = Modifier.weight(1.5f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(24.dp))
        }

        // 3. 数据列表
        LazyColumn(
            modifier = Modifier.weight(1f).border(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            itemsIndexed(uiState.featurePoints) { index, point ->
                ScriptPointItem(
                    index = index + 1,
                    point = point,
                    onDelete = { viewModel.removeFeaturePoint(point.id) },
                    onUpdateTolerance = { newTolerance ->
                        viewModel.updateFeaturePoint(point.id, point.copy(tolerance = newTolerance))
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }

        // 4. 底部配置与生成区
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                .padding(8.dp)
        ) {
            // 参数设置行
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    OutlinedButton(
                        onClick = { expandDirectionMenu = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(direction.label, fontSize = 12.sp)
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = expandDirectionMenu, onDismissRequest = { expandDirectionMenu = false }) {
                        FindDirection.values().forEach { dir ->
                            DropdownMenuItem(
                                text = { Text(dir.label) },
                                onClick = { direction = dir; expandDirectionMenu = false }
                            )
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))
                Text("相似度: ${(similarity * 100).toInt()}%", fontSize = 12.sp)
                Slider(
                    value = similarity,
                    onValueChange = { similarity = it },
                    valueRange = 0.5f..1.0f,
                    modifier = Modifier.weight(1f).height(20.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            // 动态生成代码
            val generatedCode =
                remember(uiState.featurePoints, uiState.searchRegion, colorSearchMode, similarity, direction) {
                    if (selectedTabIndex == 0 && colorSearchMode == ColorSearchMode.AREA) {
                        generateAreaFindColorCode(uiState.featurePoints, uiState.searchRegion, similarity, direction)
                    } else {
                        generateMultiPointCode(uiState.featurePoints, similarity, direction)
                    }
                }

            OutlinedTextField(
                value = generatedCode,
                onValueChange = {},
                readOnly = true,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                modifier = Modifier.fillMaxWidth().height(100.dp)
            )

            Spacer(Modifier.height(8.dp))

            // 底部操作按钮
            Row {
                Button(
                    onClick = { clipboardManager.setText(AnnotatedString(generatedCode)) },
                    modifier = Modifier.weight(1f).height(36.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("复制脚本")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { /* TODO: 调用测试逻辑 */ },
                    modifier = Modifier.weight(1f).height(36.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("运行测试")
                }
            }
        }
    }
}

// === 组件：列表单行 ===
@Composable
fun ScriptPointItem(
    index: Int,
    point: FeaturePoint,
    onDelete: () -> Unit,
    onUpdateTolerance: (String) -> Unit
) {
    val colorObj = remember(point.colorHex) {
        try {
            val hex = point.colorHex.removePrefix("#")
            if (hex.length == 6) {
                Color(hex.substring(0, 2).toInt(16), hex.substring(2, 4).toInt(16), hex.substring(4, 6).toInt(16))
            } else Color.Gray
        } catch (e: Exception) {
            Color.Gray
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$index", modifier = Modifier.weight(0.8f), fontSize = 12.sp)
        Text(
            "${point.x},${point.y}",
            modifier = Modifier.weight(1.5f),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )

        Row(modifier = Modifier.weight(1.5f), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(colorObj)
                    .border(1.dp, Color.Gray)
            )
            Spacer(Modifier.width(4.dp))
            Text(point.colorHex.replace("#", ""), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }

        Box(
            modifier = Modifier
                .weight(1.5f)
                .padding(vertical = 4.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp)
        ) {
            var text by remember(point.tolerance) { mutableStateOf(point.tolerance) }
            BasicTextField(
                value = text,
                onValueChange = {
                    text = it
                    onUpdateTolerance(it)
                },
                textStyle = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                singleLine = true
            )
        }

        IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
        }
    }
}

// === 组件：区域配置栏 ===
@Composable
fun RegionConfigSection(
    region: IntRect?,
    onSelectRegion: () -> Unit,
    onClearRegion: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(4.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("搜索范围 (Region)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = region?.let { "[${it.left}, ${it.top}, ${it.width}, ${it.height}]" } ?: "全屏 (0,0,w,h)",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        if (region != null) {
            IconButton(onClick = onClearRegion, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Delete, "清除区域", modifier = Modifier.size(16.dp))
            }
        }

        Button(
            onClick = onSelectRegion,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            modifier = Modifier.height(28.dp)
        ) {
            Icon(Icons.Default.Crop, null, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text("选取", fontSize = 12.sp)
        }
    }
}

// === 逻辑：多点找色代码生成 ===
private fun generateMultiPointCode(
    points: List<FeaturePoint>,
    similarity: Float,
    direction: FindDirection
): String {
    if (points.isEmpty()) return "// 请先在左侧取色"

    val sb = StringBuilder()
    val first = points.first()
    val simStr = String.format("%.2f", similarity)

    sb.append("// 多点找色: 主色 ${first.colorHex}\n")

    if (points.size > 1) {
        sb.append("var isMatch = images.detectsColor(img, \"${first.colorHex}\", ${first.x}, ${first.y}, \"${first.tolerance}\", [\n")
        for (i in 1 until points.size) {
            val p = points[i]
            val offsetX = p.x - first.x
            val offsetY = p.y - first.y
            sb.append("    [$offsetX, $offsetY, \"${p.colorHex}\"], // $i\n")
        }
        sb.append("]);\n")
    } else {
        sb.append("var p = findColor(img, \"${first.colorHex}\", {\n")
        sb.append("    threshold: 4,\n")
        sb.append("    similarity: $simStr\n")
        sb.append("});\n")
    }

    sb.append("if (isMatch) {\n")
    sb.append("    click(${first.x}, ${first.y});\n")
    sb.append("}")

    return sb.toString()
}

// === 逻辑：区域找色代码生成 ===
private fun generateAreaFindColorCode(
    points: List<FeaturePoint>,
    region: IntRect?,
    similarity: Float,
    direction: FindDirection
): String {
    if (points.isEmpty()) return "// 请先取一个目标颜色"

    val target = points.first()
    val sb = StringBuilder()

    // 区域定义
    val regionStr = if (region != null) {
        "[${region.left}, ${region.top}, ${region.width}, ${region.height}]"
    } else {
        "null // 全屏"
    }

    sb.append("// 区域找色: 在范围 $regionStr 内找 ${target.colorHex}\n")
    sb.append("var p = findColor(img, \"${target.colorHex}\", {\n")
    if (region != null) {
        sb.append("    region: $regionStr,\n")
    }
    sb.append("    threshold: 4,\n")
    sb.append("    similarity: ${String.format("%.2f", similarity)}\n")
    sb.append("});\n\n")

    sb.append("if (p) {\n")
    sb.append("    console.log(\"找到坐标: \" + p.x + \",\" + p.y);\n")
    sb.append("    click(p.x, p.y);\n")
    sb.append("}")

    return sb.toString()
}