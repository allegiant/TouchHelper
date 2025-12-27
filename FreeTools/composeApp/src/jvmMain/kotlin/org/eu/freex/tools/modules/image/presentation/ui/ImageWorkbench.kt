package org.eu.freex.tools.modules.image.presentation.ui


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
// 引入 Feature 层的组件 (请确保这些组件已按之前的方案创建)
import org.eu.freex.tools.modules.image.presentation.ui.components.ProjectExplorer  // 原 LeftPanel
import org.eu.freex.tools.modules.image.presentation.ui.components.EditorCanvas     // 原 Workspace
import org.eu.freex.tools.modules.image.presentation.ui.components.ProcessingPipeline // 原 BottomPanel
import org.eu.freex.tools.modules.image.presentation.ui.components.InspectorPanel   // 原 RightPanel
import org.eu.freex.tools.modules.image.presentation.viewmodel.ImageViewModel
import org.eu.freex.tools.modules.image.presentation.contract.ImageUiEvent
import org.eu.freex.tools.dialogs.CharMappingDialog
import org.eu.freex.tools.dialogs.ScreenCropperDialog

@Composable
fun ImageWorkbench(
    // 允许外部传入 ViewModel (便于测试或共享)，默认内部创建
    viewModel: ImageViewModel = remember { ImageViewModel() }
) {
    // 1. 监听唯一的 UI 状态流
    val state by viewModel.uiState.collectAsState()

    // 2. 根布局
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF252526))) {
        Row(modifier = Modifier.fillMaxSize()) {

            // ------------------------------------------------------------
            // 左侧：资源管理器 (Project Explorer)
            // ------------------------------------------------------------
            ProjectExplorer(
                modifier = Modifier.width(260.dp).fillMaxHeight(),
                // 数据传递：只传 State 中需要的部分
                sourceImages = state.sourceImages,
                selectedIndex = state.selectedSourceIndex,
                // 事件传递：统一封装为 Event
                onSelect = { index -> viewModel.handleEvent(ImageUiEvent.SelectSourceImage(index)) },
                onImportFile = { file -> viewModel.handleEvent(ImageUiEvent.LoadFile(file)) },
                onScreenCapture = { viewModel.handleEvent(ImageUiEvent.StartScreenCapture) },
                onRemove = { index -> viewModel.handleEvent(ImageUiEvent.RemoveSourceImage(index)) }
            )

            // ------------------------------------------------------------
            // 中间：编辑区域 (Editor Area)
            // ------------------------------------------------------------
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {

                // A. 画布 (Editor Canvas)
                EditorCanvas(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    // 核心显示逻辑：显示流水线结果 或 原图
                    workImage = state.activeDisplayImage,
                    // 辅助显示数据
                    binaryPreview = state.binaryPreview, // 二值化预览层
                    activeRects = state.activeRects,     // 切割框
                    scale = state.mainScale,
                    offset = state.mainOffset,
                    hoverColor = state.hoverColor,
                    hoverPos = state.hoverPixelPos,
                    // 交互事件
                    onTransformChange = { s, o ->
                        viewModel.handleEvent(ImageUiEvent.UpdateCanvasTransform(s, o))
                    },
                    onHover = { pos, color ->
                        viewModel.handleEvent(ImageUiEvent.HoverCanvas(pos, color))
                    },
                    onRectClick = { rect ->
                        viewModel.handleEvent(ImageUiEvent.OpenMappingDialog(rect))
                    },
                    onColorPick = { hex ->
                        viewModel.handleEvent(ImageUiEvent.ColorPick(hex))
                    }
                )

                // B. 处理流水线 (Processing Pipeline)
                ProcessingPipeline(
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    processChain = state.displayChain,
                    selectedIndex = state.selectedPipelineIndex,
                    onSelect = { index -> viewModel.handleEvent(ImageUiEvent.SelectPipelineStep(index)) },
                    onDelete = { index -> viewModel.handleEvent(ImageUiEvent.DeletePipelineStep(index)) }
                )
            }

            // ------------------------------------------------------------
            // 右侧：属性检查器 (Inspector Panel)
            // ------------------------------------------------------------
            InspectorPanel(
                modifier = Modifier.width(320.dp).fillMaxHeight(),
                // 面板状态
                selectedTab = state.rightPanelTabIndex,
                // 滤镜相关状态
                currentFilter = state.currentFilter,
                thresholdRange = state.thresholdRange,
                isRgbAvgEnabled = state.isRgbAvgEnabled,
                colorRules = state.activeColorRules, // 自动判断是全局还是局部规则
                // 切割相关状态
                isGridMode = state.isGridMode,
                gridParams = state.gridParams,

                // 事件回调
                onTabChange = { index -> viewModel.handleEvent(ImageUiEvent.ChangePanelTab(index)) },

                // 滤镜事件
                onFilterChange = { filter -> viewModel.handleEvent(ImageUiEvent.SelectFilter(filter)) },
                onThresholdChange = { range -> viewModel.handleEvent(ImageUiEvent.UpdateThreshold(range)) },
                onRgbAvgChange = { enabled -> viewModel.handleEvent(ImageUiEvent.ToggleRgbAvg(enabled)) },
                onApplyFilter = { viewModel.handleEvent(ImageUiEvent.ApplyCurrentFilter) },

                // 规则事件
                onRuleUpdate = { id, bias -> viewModel.handleEvent(ImageUiEvent.UpdateColorRule(id, bias)) },
                onRuleToggle = { id, enabled -> viewModel.handleEvent(ImageUiEvent.ToggleColorRule(id, enabled)) },
                onRuleRemove = { id -> viewModel.handleEvent(ImageUiEvent.RemoveColorRule(id)) },

                // 切割事件
                onGridModeToggle = { isGrid -> viewModel.handleEvent(ImageUiEvent.ToggleGridMode(isGrid)) },
                onGridParamsChange = { params -> viewModel.handleEvent(ImageUiEvent.UpdateGridParams(params)) },
                onPerformSegmentation = { viewModel.handleEvent(ImageUiEvent.PerformSegmentation) }
            )
        }

        // ------------------------------------------------------------
        // 全局弹窗层 (Dialogs)
        // ------------------------------------------------------------

        // 1. 屏幕截图裁剪器
        if (state.isScreenCropperVisible && state.fullScreenCapture != null) {
            ScreenCropperDialog(
                fullScreenImage = state.fullScreenCapture!!,
                onDismiss = { viewModel.handleEvent(ImageUiEvent.DismissDialogs) },
                onCropConfirm = { croppedImage ->
                    viewModel.handleEvent(ImageUiEvent.ConfirmScreenCrop(croppedImage))
                }
            )
        }

        // 2. 字库映射对话框
        if (state.isMappingDialogVisible && state.mappingBitmap != null) {
            CharMappingDialog(
                // 注意：State 中存的是 BufferedImage，Dialog 需要 ImageBitmap
                bitmap = state.mappingBitmap!!.toComposeImageBitmap(),
                onDismiss = { viewModel.handleEvent(ImageUiEvent.DismissDialogs) },
                onConfirm = { char ->
                    viewModel.handleEvent(ImageUiEvent.ConfirmMapping(char))
                }
            )
        }

        // 3. Loading 指示器
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}