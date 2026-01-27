package org.eu.freex.tools.modules.image.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import org.eu.freex.tools.common.components.LoadingOverlay
import org.eu.freex.tools.common.components.ToastOverlay
import org.eu.freex.tools.common.model.WorkbenchTab
import org.eu.freex.tools.modules.image.presentation.features.editor.EditorCanvasPanel
import org.eu.freex.tools.modules.image.presentation.features.filter.components.InspectorPanel
import org.eu.freex.tools.modules.image.presentation.features.pipeline.ProcessingPipeline
import org.eu.freex.tools.modules.image.presentation.features.project.ProjectListPanel
import org.eu.freex.tools.modules.image.presentation.features.tools.dialogs.CodeGenDialog
import org.eu.freex.tools.modules.image.presentation.features.tools.dialogs.ScreenCropperDialog
import org.eu.freex.tools.modules.image.presentation.viewmodel.EditorCanvasViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.MainViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.ProjectListViewModel

@Composable
fun ImageWorkbench(
    mainViewModel: MainViewModel = koinInject(),
    projectListViewModel: ProjectListViewModel = koinInject(),
    editorViewModel: EditorCanvasViewModel = koinInject()
) {
    val mainState by mainViewModel.uiState.collectAsState()
    val editorState by editorViewModel.uiState.collectAsState()

    // Workbench 级状态
    var currentTab by remember { mutableStateOf(WorkbenchTab.FILTER) }
    var showCodeDialog by remember { mutableStateOf(false) }
    var generatedCode by remember { mutableStateOf("") }
    // 框选状态保留在这里，因为 InspectorPanel 和 Canvas 都要用
    var isSelectingRegion by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {

            // 1. 左侧：资源列表与操作按钮
            Column(modifier = Modifier.width(260.dp).fillMaxHeight()) {
                // [修复] 补回屏幕截图按钮
                Button(
                    onClick = { projectListViewModel.captureScreen() },
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp)
                ) {
                    Text("屏幕截图")
                }

                // [修复] 补回生成脚本按钮
                Button(
                    onClick = {
                        generatedCode = mainViewModel.generateScript()
                        showCodeDialog = true
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("🛠️ 生成运行脚本")
                }

                // 资源列表
                ProjectListPanel(modifier = Modifier.weight(1f))
            }

            // 2. 中间：画布与流水线
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                // [核心集成] 新的 EditorCanvasPanel
                EditorCanvasPanel(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    currentTab = currentTab,
                    isSelectingRegion = isSelectingRegion,
                    onRegionSelectEnd = { rect ->
                        // 更新 VM 搜索区域
                        editorViewModel.updateSearchRegion(rect.left, rect.top, rect.width, rect.height)
                        isSelectingRegion = false
                    },
                    onRegionSelectCancel = {
                        isSelectingRegion = false
                    }
                )

                ProcessingPipeline(
                    modifier = Modifier.fillMaxWidth().height(112.dp)
                )
            }

            // 3. 右侧：属性面板
            InspectorPanel(
                modifier = Modifier.width(320.dp).fillMaxHeight(),
                currentTab = currentTab,
                onTabChange = { currentTab = it },
                // 触发框选
                onStartRegionSelect = { isSelectingRegion = true }
            )
        }

        // --- 全局弹窗层 ---
        if (showCodeDialog) {
            CodeGenDialog(code = generatedCode, onDismiss = { showCodeDialog = false })
        }

        editorState.cropperLayer?.let { layer ->
            ScreenCropperDialog(
                imageLayer = layer,
                onConfirm = { rect -> editorViewModel.confirmCrop(rect) },
                onDismiss = { editorViewModel.exitCropMode() }
            )
        }

        if (mainState.isLoading) {
            LoadingOverlay(message = mainState.loadingMessage)
        }
        mainState.toastMessage?.let { msg ->
            ToastOverlay(message = msg, onDismiss = mainViewModel::clearToast)
        }
    }
}