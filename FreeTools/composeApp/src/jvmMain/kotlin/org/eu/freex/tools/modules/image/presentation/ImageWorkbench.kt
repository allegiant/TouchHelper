package org.eu.freex.tools.modules.image.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject

// 引入各模块组件
import org.eu.freex.tools.modules.image.presentation.features.editor.EditorCanvasPanel
import org.eu.freex.tools.modules.image.presentation.features.filter.components.InspectorPanel
import org.eu.freex.tools.modules.image.presentation.features.pipeline.ProcessingPipeline
import org.eu.freex.tools.modules.image.presentation.features.project.ProjectListPanel
import org.eu.freex.tools.modules.image.presentation.features.tools.dialogs.ScreenCropperDialog

// 引入 ViewModels
import org.eu.freex.tools.modules.image.presentation.viewmodel.EditorCanvasViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.MainViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.ProjectListViewModel
import org.eu.freex.tools.common.components.LoadingOverlay // 假设你有这个通用组件
import org.eu.freex.tools.common.components.ToastOverlay   // 假设你有这个通用组件
import org.eu.freex.tools.common.model.WorkbenchTab
import org.eu.freex.tools.modules.image.presentation.features.tools.dialogs.CodeGenDialog

@Composable
fun ImageWorkbench(
    mainViewModel: MainViewModel = koinInject(),
    projectListViewModel: ProjectListViewModel = koinInject(),
    editorViewModel: EditorCanvasViewModel = koinInject()
) {
    // 1. 监听全局状态
    val mainState by mainViewModel.uiState.collectAsState()
    val editorState by editorViewModel.uiState.collectAsState()

    // [新增] 状态管理：控制生成代码弹窗的显示与内容
    var showCodeDialog by remember { mutableStateOf(false) }
    var generatedCode by remember { mutableStateOf("") }

    // 2. [关键] 状态提升：管理当前的 Tab (Filter vs Segmentation)
    // 必须放在这里，因为 Canvas 需要知道是否显示切割覆盖层，而 Inspector 需要切换它
    var currentTab by remember { mutableStateOf(WorkbenchTab.FILTER) }

    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {

            // --- 左侧：资源列表 ---
            Column(modifier = Modifier.width(260.dp).fillMaxHeight()) {
                Button(
                    onClick = { projectListViewModel.captureScreen() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("屏幕截图")
                }
                // [新增] 生成脚本按钮
                Button(
                    onClick = {
                        // 1. 调用 VM 生成代码
                        generatedCode = mainViewModel.generateScript()
                        // 2. 显示弹窗
                        showCodeDialog = true
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("🛠️ 生成运行脚本")
                }
                // ProjectListPanel 内部已注入 VM，无需传参
                ProjectListPanel(modifier = Modifier.weight(1f))
            }

            // --- 中间：画布与流水线 ---
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                // 1. 画布
                EditorCanvasPanel(
                    modifier = Modifier.weight(1f),
                    // 仅当 Tab 为 切割识别 时，显示红色覆盖层
                    showSegmentationOverlay = (currentTab == WorkbenchTab.SEGMENTATION)
                    // 其他交互事件已在 EditorCanvasPanel 内部直接对接 ViewModel
                )

                // 2. 流水线
                ProcessingPipeline(
                    modifier = Modifier.fillMaxWidth().height(112.dp)
                    // 数据源已在组件内部通过 Koin 注入
                )
            }

            // --- 右侧：属性面板 ---
            InspectorPanel(
                modifier = Modifier.width(320.dp).fillMaxHeight(),
                // 将 Tab 状态下放
                currentTab = currentTab,
                onTabChange = { currentTab = it }
            )
        }

        // --- 全局弹窗层 ---
        // [新增] 代码生成结果弹窗
        if (showCodeDialog) {
            CodeGenDialog(
                code = generatedCode,
                onDismiss = { showCodeDialog = false }
            )
        }

        // 1. 裁剪对话框 (受 EditorViewModel 控制)
        editorState.cropperLayer?.let { layer ->
            ScreenCropperDialog(
                imageLayer = layer,
                onConfirm = { rect -> editorViewModel.confirmCrop(rect) },
                onDismiss = { editorViewModel.exitCropMode() }
            )
        }

        // 2. Loading 遮罩
        if (mainState.isLoading) {
            LoadingOverlay(message = mainState.loadingMessage)
        }

        // 3. Toast 提示
        mainState.toastMessage?.let { msg ->
            ToastOverlay(message = msg, onDismiss = mainViewModel::clearToast)
        }

        // 4. 错误提示 (可选)
        /*
        mainState.errorMessage?.let { error ->
            ErrorDialog(text = error, onDismiss = mainViewModel::clearError)
        }
        */
    }
}