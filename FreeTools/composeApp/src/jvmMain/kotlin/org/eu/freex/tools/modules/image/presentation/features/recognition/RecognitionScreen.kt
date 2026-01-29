package org.eu.freex.tools.modules.image.presentation.features.recognition

// 引入我们的通用画布和策略
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.eu.freex.tools.modules.image.domain.model.ImageLayer
import org.eu.freex.tools.modules.image.presentation.features.editor.EditorCanvasContent
import org.eu.freex.tools.modules.image.presentation.features.editor.strategies.RecognitionStrategy
import org.eu.freex.tools.modules.image.presentation.viewmodel.EditorCanvasViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.RecognitionViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecognitionScreen(
    onBack: () -> Unit,
    viewModel: RecognitionViewModel = koinInject(),
    editorViewModel: EditorCanvasViewModel = koinInject()
) {
    val state by viewModel.uiState.collectAsState()

    // [适配] 收集变换状态 (scale/pan)
    // 传递给 Content 使用，以利用 graphicsLayer 优化
    val transformState = editorViewModel.transformState.collectAsState()

    val bufferedImage = state.displayImage

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OCR 识别结果") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (bufferedImage != null) {
                // [核心] 构造 ImageLayer 给万能画布使用
                // 这里使用 remember 避免重组时重复创建对象
                val displayImageLayer = remember(bufferedImage) {
                    ImageLayer(name = "ocr_preview", image = bufferedImage)
                }

                EditorCanvasContent(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    displayImage = displayImageLayer,
                    transformState = transformState,
                    onTransform = { zoom, pan ->
                        editorViewModel.updateTransform(zoom, pan)
                    }
                )

                // 底部简单的文本列表 (可选，保留原有的列表视图)
                //RecognitionResultList(results = state.results, ...)
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text("暂无图片数据")
                }
            }
        }
    }
}