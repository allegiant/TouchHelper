package org.eu.freex.tools.modules.image.presentation.features.recognition

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.eu.freex.tools.modules.image.domain.model.RecognitionResult
import org.eu.freex.tools.modules.image.presentation.viewmodel.RecognitionUiState
import org.eu.freex.tools.modules.image.presentation.viewmodel.RecognitionViewModel
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.collections.forEach
import kotlin.math.roundToInt

@Composable
fun RecognitionScreen(
    viewModel: RecognitionViewModel
) {
    val state by viewModel.uiState.collectAsState()

    // 加载图片的 Bitmap (用于显示)
    var imageBitmap by remember(state.selectedImage) { mutableStateOf<ImageBitmap?>(null) }
    var imageSize by remember { mutableStateOf<Size?>(null) }

    // 简单的图片加载逻辑 (放在 LaunchedEffect 里以免阻塞 UI)
    LaunchedEffect(state.selectedImage) {
        state.selectedImage?.let { file ->
            try {
                val bufferedImage: BufferedImage = ImageIO.read(file)
                imageBitmap = bufferedImage.toComposeImageBitmap()
                imageSize = Size(bufferedImage.width.toFloat(), bufferedImage.height.toFloat())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // --- 顶部控制栏 ---
        TopControlBar(
            state = state,
            onSelectImage = { viewModel.onImageSelected(it) },
            onRunTest = { viewModel.runTest() }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- 主内容区域 (左右分栏) ---
        Row(modifier = Modifier.fillMaxSize()) {
            // 左侧：图片可视化区域 (占 70% 宽度)
            Box(
                modifier = Modifier
                    .weight(0.7f)
                    .fillMaxHeight()
                    .border(1.dp, Color.Gray)
                    .background(Color.DarkGray) // 深色背景方便看图
            ) {
                if (imageBitmap != null && imageSize != null) {
                    // 核心：带绘制功能的图片展示
                    RecognitionCanvas(
                        bitmap = imageBitmap!!,
                        originalSize = imageSize!!,
                        results = state.results
                    )
                } else {
                    Text("请选择一张图片开始测试", color = Color.White, modifier = Modifier.align(Alignment.Center))
                }

                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                // 2. 🌟 新增：显示错误信息
                state.error?.let { err ->
                    Card(
                        backgroundColor = Color.Red,
                        modifier = Modifier.align(Alignment.Center).padding(16.dp)
                    ) {
                        Text(
                            text = "错误: $err",
                            color = Color.White,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

// 3. 🌟 新增：显示无结果提示 (如果加载完了，没错误，且结果为空)
                if (!state.isLoading && state.error == null && state.results.isEmpty() && state.selectedImage != null) {
                    Text(
                        text = "未识别到任何字符\n请检查：\n1. 切割参数是否适用于该图片\n2. 字库是否为空\n3. 阈值是否过高",
                        color = Color.Yellow,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 右侧：结果列表 (占 30% 宽度)
            ResultListPanel(
                modifier = Modifier.weight(0.3f).fillMaxHeight(),
                results = state.results,
                timeCost = state.timeCostMs
            )
        }
    }
}

// --- 子组件：顶部控制栏 ---
@Composable
private fun TopControlBar(
    state: Any, // 这里简化类型
    onSelectImage: (File) -> Unit,
    onRunTest: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // 这里只是示意，您可以使用您项目现有的 FilePicker
        Button(onClick = {
            val fileChooser = javax.swing.JFileChooser()
            if (fileChooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
                onSelectImage(fileChooser.selectedFile)
            }
        }) {
            Text("选择测试图片")
        }

        Spacer(modifier = Modifier.width(16.dp))

        Button(
            onClick = onRunTest,
            // 只有选了图且不在加载时才能点
            enabled = (state as? RecognitionUiState)?.selectedImage != null &&
                    !(state as RecognitionUiState).isLoading
        ) {
            Text("开始识别")
        }
    }
}

// --- 子组件：核心画布 (处理坐标映射) ---
@Composable
private fun RecognitionCanvas(
    bitmap: androidx.compose.ui.graphics.ImageBitmap,
    originalSize: Size,
    results: List<RecognitionResult>
) {
    // 使用 BoxWithConstraints 获取当前显示的实际尺寸，以便做坐标缩放
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // 计算缩放比例：保持长宽比 (Fit 模式)
        val containerAspectRatio = maxWidth / maxHeight
        val imageAspectRatio = originalSize.width / originalSize.height

        // 计算实际渲染的图片大小
        val renderScale = if (containerAspectRatio > imageAspectRatio) {
            maxHeight.value / originalSize.height // 高度占满，宽度有黑边
        } else {
            maxWidth.value / originalSize.width   // 宽度占满，高度有黑边
        }

        val renderWidth = originalSize.width * renderScale
        val renderHeight = originalSize.height * renderScale

        // 显示图片
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.size(renderWidth.dp, renderHeight.dp),
            contentScale = ContentScale.Fit
        )

        // 在图片上方覆盖一层 Canvas 用于画框
        Canvas(modifier = Modifier.size(renderWidth.dp, renderHeight.dp)) {
            // 1. 确保缩放比例是 Float
            val scaleX = size.width / originalSize.width
            val scaleY = size.height / originalSize.height

            results.forEach { result ->
                val rect = result.rect

                // 🔴 修复点：必须先调用 .toFloat() 将 UInt/Int 转为浮点数，再乘缩放比例
                // 注意：根据您的 UniFFI 定义，left/top 可能是 Int，width/height 可能是 UInt，统统转 Float
                val left = rect.left.toFloat() * scaleX
                val top = rect.top.toFloat() * scaleY
                val width = rect.width.toFloat() * scaleX
                val height = rect.height.toFloat() * scaleY

                // 2. 画红框 (Size 和 Offset 都需要 Float)
                drawRect(
                    color = Color.Red,
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        // 渲染文字标签 (使用 Box + Offset 绝对定位，比 Canvas drawText 更简单且兼容性更好)
        // 注意：这种方式如果字太多可能会卡，但测试够用了
        results.forEach { result ->
            val rect = result.rect
            val scale = renderScale // 使用外层的 dp scale

            // 计算显示的 dp 坐标
            val leftDp = (rect.left * scale)
            val topDp = (rect.top * scale) - 20 // 显示在框上方

            Text(
                text = "${result.char} (${(result.confidence * 100).toInt()}%)",
                color = Color.Green,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier
                    .offset { IntOffset(leftDp.roundToInt(), topDp.roundToInt()) }
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(2.dp)
            )
        }
    }
}

// --- 子组件：右侧列表 ---
@Composable
private fun ResultListPanel(
    modifier: Modifier,
    results: List<RecognitionResult>,
    timeCost: Long
) {
    Card(modifier = modifier, elevation = 4.dp) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text("识别结果统计", style = MaterialTheme.typography.h6)
            Text("耗时: ${timeCost}ms", style = MaterialTheme.typography.caption)
            Text("数量: ${results.size}", style = MaterialTheme.typography.caption)
            Divider(modifier = Modifier.padding(vertical = 8.dp))

            LazyColumn {
                items(results) { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 字符大字
                        Text(
                            text = item.char,
                            style = MaterialTheme.typography.h5,
                            color = Color.Blue,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(40.dp)
                        )
                        Column {
                            Text("置信度: ${(item.confidence * 100).toInt()}%")
                            Text(
                                "坐标: (${item.rect.left}, ${item.rect.top})",
                                style = MaterialTheme.typography.caption
                            )
                        }
                    }
                    Divider()
                }
            }
        }
    }
}