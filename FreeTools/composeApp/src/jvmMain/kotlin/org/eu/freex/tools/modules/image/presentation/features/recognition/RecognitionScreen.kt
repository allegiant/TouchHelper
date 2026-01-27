package org.eu.freex.tools.modules.image.presentation.features.recognition

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import org.koin.compose.koinInject
import org.eu.freex.tools.modules.image.domain.model.ImageLayer
import org.eu.freex.tools.modules.image.presentation.viewmodel.RecognitionViewModel
// 引入我们的通用画布和策略
import org.eu.freex.tools.modules.image.presentation.features.editor.EditorCanvasContent
import org.eu.freex.tools.modules.image.presentation.features.editor.strategies.RecognitionStrategy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecognitionScreen(
    onBack: () -> Unit,
    viewModel: RecognitionViewModel = koinInject()
) {
    val state by viewModel.uiState.collectAsState()
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

                // [核心] 构造策略
                val strategy = remember(state.results) {
                    RecognitionStrategy(state.results)
                }


                EditorCanvasContent(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    displayImage = displayImageLayer,
                    strategy = strategy
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