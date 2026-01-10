package org.eu.freex.tools.modules.image.presentation.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.eu.freex.tools.modules.image.application.WorkspaceUseCase
import org.eu.freex.tools.modules.image.domain.model.ImageFilter
import org.eu.freex.tools.modules.image.domain.model.ImageLayer
import org.eu.freex.tools.modules.image.domain.model.ImageWorkspace
import org.eu.freex.tools.modules.image.domain.model.LayerConfig
import org.eu.freex.tools.modules.image.domain.model.SegmentationProject
import org.eu.freex.tools.modules.image.presentation.core.ApplyFilterStep
import org.eu.freex.tools.modules.image.presentation.core.CancelColorPick
import org.eu.freex.tools.modules.image.presentation.core.CancelPreview
import org.eu.freex.tools.modules.image.presentation.core.ConfirmCrop
import org.eu.freex.tools.modules.image.presentation.core.DismissCropper
import org.eu.freex.tools.modules.image.presentation.core.ExportDisplayImage
import org.eu.freex.tools.modules.image.presentation.core.ExportImage
import org.eu.freex.tools.modules.image.presentation.core.ImageUiEvent
import org.eu.freex.tools.modules.image.presentation.core.ImageUiState
import org.eu.freex.tools.modules.image.presentation.core.LoadFile
import org.eu.freex.tools.modules.image.presentation.core.LoadProject
import org.eu.freex.tools.modules.image.presentation.core.PreviewFilter
import org.eu.freex.tools.modules.image.presentation.core.RemoveAsset
import org.eu.freex.tools.modules.image.presentation.core.SaveProject
import org.eu.freex.tools.modules.image.presentation.core.SelectAsset
import org.eu.freex.tools.modules.image.presentation.core.SelectChar
import org.eu.freex.tools.modules.image.presentation.core.SelectStep
import org.eu.freex.tools.modules.image.presentation.core.StartScreenCapture
import org.eu.freex.tools.modules.image.presentation.core.StopLabeling
import org.eu.freex.tools.modules.image.presentation.core.SubmitLabelAndNext
import org.eu.freex.tools.modules.image.presentation.core.SwitchTab
import org.eu.freex.tools.modules.image.presentation.core.TriggerColorPick
import org.eu.freex.tools.modules.image.presentation.core.UpdateFilterStep
import org.eu.freex.tools.modules.image.presentation.core.UpdateSegmentationConfig
import org.eu.freex.tools.modules.image.presentation.core.WorkbenchTab

class ImageViewModel(
    private val useCase: WorkspaceUseCase
) : ViewModel() {

    private var workspace = ImageWorkspace()
    private val _uiState = MutableStateFlow(ImageUiState())
    val uiState = _uiState.asStateFlow()

    private data class PreviewRequest(val baseLayer: ImageLayer, val filter: ImageFilter)

    // 使用 CONFLATED 通道解决积压
    private val previewChannel = Channel<PreviewRequest>(Channel.CONFLATED)

    // 用于在挂起函数和事件处理之间传递颜色的通道
    private val colorPickChannel = Channel<Color>(Channel.RENDEZVOUS)

    init {
        viewModelScope.launch {
            previewChannel.consumeEach { request ->
                try {
                    // 执行耗时计算
                    val resultLayer = useCase.calculatePreview(request.baseLayer, request.filter)

                    if (resultLayer != null) {
                        _uiState.update { currentState ->
                            val currentPreview = currentState.previewLayer

                            // 1. 校验：如果当前已经退出了预览模式，就丢弃结果
                            if (currentPreview == null) return@update currentState

                            // 2. 防抖动与乐观更新校验
                            val resultFilter = resultLayer.activeFilter
                            val currentFilter = currentPreview.activeFilter

                            if (resultFilter != null && currentFilter != null &&
                                resultFilter::class == currentFilter::class
                            ) {
                                // 保留当前 UI 的 config (因为用户可能又拖动了滑块)，只更新图像数据
                                currentState.copy(
                                    previewLayer = resultLayer.copy(config = currentPreview.config)
                                )
                            } else {
                                currentState.copy(previewLayer = resultLayer)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        setupReactiveSegmentation()
    }

    @OptIn(FlowPreview::class)
    private fun setupReactiveSegmentation() {
        // 监听显示的图片
        val imageFlow = _uiState.map { it.displayImage }.distinctUntilChanged()

        // 监听 Domain 中的配置 (通过 uiState 间接监听)
        val configFlow = _uiState
            .map { it.segmentationProject?.config }
            .distinctUntilChanged()
            .debounce(200)

        val tabFlow = _uiState.map { it.activeTab }.distinctUntilChanged()

        combine(imageFlow, configFlow, tabFlow) { layer, config, tab ->
            Triple(layer, config, tab)
        }.onEach { (layer, config, tab) ->
            // 只有在 切割Tab 且有图 且有配置 时才执行
            if (tab == WorkbenchTab.SEGMENTATION && layer?.image != null && config != null) {
                // 调用 UseCase (不接触 Rust)
                useCase.performSegmentation(layer.image, config)
                    .onSuccess { rects ->
                        // 更新 Workspace
                        val currentProject = workspace.segmentation ?: SegmentationProject(config = config)
                        workspace = workspace.copy(
                            segmentation = currentProject.copy(results = rects)
                        )
                        refreshUiState()
                    }
                    .onFailure { it.printStackTrace() }
            }
        }.launchIn(viewModelScope)
    }

    fun handleEvent(event: ImageUiEvent) {
        viewModelScope.launch {
            // Loading 状态处理... 预览和取色不触发Loading
            if (event !is PreviewFilter && event !is TriggerColorPick
                && event !is SwitchTab && event !is UpdateSegmentationConfig && event !is SelectChar) {
                _uiState.update { it.copy(isLoading = true) }
            }

            runCatching {
                when (event) {
                    is LoadFile -> {
                        useCase.importAsset(event.file).onSuccess { layer ->
                            var newWorkspace = workspace.copy(assets = workspace.assets + layer)
                            useCase.activateAsset(newWorkspace, layer.id)
                                .onSuccess { finalWorkspace ->
                                    workspace = finalWorkspace
                                    _uiState.update { s -> s.copy(previewLayer = null) }
                                }
                                .onFailure {
                                    workspace = newWorkspace
                                    it.printStackTrace()
                                }
                        }
                    }

                    is SelectAsset -> {
                        useCase.activateAsset(workspace, event.assetId)
                            .onSuccess {
                                workspace = it
                                _uiState.update { s -> s.copy(previewLayer = null) }
                            }
                            .onFailure { it.printStackTrace() }
                    }

                    is RemoveAsset -> {
                        workspace = useCase.removeAsset(workspace, event.assetId)
                        _uiState.update { s -> s.copy(previewLayer = null) }
                    }

                    is SaveProject -> {
                        useCase.saveWorkspace(event.file, workspace).onFailure { it.printStackTrace() }
                    }
                    is LoadProject -> {
                        useCase.loadWorkspace(event.file).onSuccess { loadedWorkspace ->
                            workspace = loadedWorkspace
                            _uiState.update { s -> s.copy(previewLayer = null) }
                            refreshUiState()
                        }.onFailure { it.printStackTrace() }
                    }

                    is ExportDisplayImage -> {
                        _uiState.value.displayImage?.let { useCase.exportImage(it, event.file) }
                    }

                    is ExportImage -> useCase.exportImage(event.layer, event.file)

                    is StartScreenCapture -> useCase.captureScreen().onSuccess {
                        _uiState.update { s -> s.copy(cropperLayer = it, isLoading = false) }
                        return@launch
                    }

                    is ConfirmCrop -> useCase.cropImage(event.sourceLayer, event.rect).onSuccess { croppedLayer ->
                        val workspaceWithAsset = workspace.copy(assets = workspace.assets + croppedLayer)
                        useCase.activateAsset(workspaceWithAsset, croppedLayer.id)
                            .onSuccess { finalWorkspace ->
                                workspace = finalWorkspace
                                _uiState.update { s -> s.copy(cropperLayer = null) }
                            }
                            .onFailure { e ->
                                e.printStackTrace()
                                workspace = workspaceWithAsset
                                _uiState.update { s -> s.copy(cropperLayer = null) }
                            }
                    }

                    is DismissCropper -> {
                        _uiState.update { s -> s.copy(cropperLayer = null) }
                        return@launch
                    }

                    // --- 预览逻辑 ---
                    is CancelPreview -> {
                        _uiState.update { s -> s.copy(previewLayer = null) }
                    }

                    // --- 取色事件处理 ---
                    is TriggerColorPick -> {
                        // 收到 Canvas 的点击颜色，发送到通道，唤醒 awaitColorPick
                        colorPickChannel.send(event.color)
                        // UI 状态的 isColorPicking = false 会在 awaitColorPick 的 finally 块中自动处理
                    }

                    is CancelColorPick -> {
                        // 关闭通道或发送空，这里简单处理为取消当前的协程等待
                        // 在 awaitColorPick 中并未直接处理 cancel，但 UI 可以通过 Job 取消
                        // 简单做法：重置 UI 状态即可，awaitColorPick 会因为超时或界面销毁而结束
                        _uiState.update { it.copy(isColorPicking = false) }
                    }

                    is PreviewFilter -> {
                        val chain = workspace.pipeline
                        if (chain != null) {
                            val idx = chain.activeIndex
                            val baseLayer = if (idx <= 0) {
                                workspace.assets.find { it.id == chain.inputAssetId }
                            } else {
                                chain.steps.getOrNull(idx - 1)
                            }

                            if (baseLayer?.image != null) {
                                // 乐观更新：立即设置 Config 以响应 UI 拖动，图片复用旧的或当前的
                                val currentImage = _uiState.value.previewLayer?.image
                                    ?: chain.getActiveLayer(workspace.assets)?.image
                                    ?: baseLayer.image

                                _uiState.update {
                                    it.copy(
                                        previewLayer = ImageLayer(
                                            name = "Previewing...",
                                            image = currentImage,
                                            config = LayerConfig.Filter(event.filter)
                                        ),
                                        isLoading = false
                                    )
                                }
                                previewChannel.trySend(PreviewRequest(baseLayer, event.filter))
                                return@launch
                            }
                        }
                    }

                    // 【新增】应用指定 Filter 到新步骤
                    is ApplyFilterStep -> {
                        useCase.addFilterStep(workspace, event.filter).onSuccess {
                            workspace = it
                            _uiState.update { s -> s.copy(previewLayer = null) }
                        }
                    }

                    // 【新增】更新当前步骤为指定 Filter
                    is UpdateFilterStep -> {
                        useCase.updateFilterStep(workspace, event.filter).onSuccess {
                            workspace = it
                            _uiState.update { s -> s.copy(previewLayer = null) }
                        }
                    }

                    is SelectStep -> {
                        workspace.pipeline?.let {
                            workspace = workspace.copy(pipeline = it.copy(activeIndex = event.index))
                        }
                        _uiState.update { s -> s.copy(previewLayer = null) }
                    }

                    // [新增] 切换 Tab
                    is SwitchTab -> {
                        // 初始化数据
                        if (event.tab == WorkbenchTab.SEGMENTATION && workspace.segmentation == null) {
                            workspace = workspace.copy(segmentation = SegmentationProject())
                        }
                        _uiState.update { it.copy(activeTab = event.tab) }
                    }

                    // [新增] 更新配置 -> 更新 Workspace -> 触发 Flow 计算
                    is UpdateSegmentationConfig -> {
                        val current = workspace.segmentation ?: SegmentationProject()
                        workspace = workspace.copy(
                            segmentation = current.copy(config = event.config)
                        )
                    }

                    // [新增] 选中字符 -> 更新 UI 瞬时状态
                    is SelectChar -> {
                        _uiState.update {
                            it.copy(segmentationInteraction = it.segmentationInteraction.copy(
                                selectedIndex = event.index,
                                isLabeling = true
                            ))
                        }
                    }

                    // [新增] 提交标注
                    is SubmitLabelAndNext -> {
                        val currentProject = workspace.segmentation
                        val currentIndex = _uiState.value.segmentationInteraction.selectedIndex

                        if (currentProject != null && currentIndex != -1) {
                            // 1. 更新业务数据
                            val newLabels = currentProject.labels.toMutableMap()
                            newLabels[currentIndex] = event.text
                            workspace = workspace.copy(
                                segmentation = currentProject.copy(labels = newLabels)
                            )

                            // 2. 更新 UI 交互 (游标移动)
                            val nextIndex = (currentIndex + 1).coerceAtMost(currentProject.results.size - 1)
                            _uiState.update {
                                it.copy(segmentationInteraction = it.segmentationInteraction.copy(
                                    selectedIndex = nextIndex,
                                    isLabeling = true
                                ))
                            }
                        }
                    }

                    is StopLabeling -> {
                        _uiState.update {
                            it.copy(segmentationInteraction = it.segmentationInteraction.copy(isLabeling = false))
                        }
                    }
                }
            }.onFailure { it.printStackTrace() }

            refreshUiState()
        }
    }

    private fun refreshUiState() {
        _uiState.update {
            it.copy(
                isLoading = false,
                assets = workspace.assets,
                activeChain = workspace.pipeline,
                segmentationProject = workspace.segmentation
            )
        }
    }

    /**
     * 【核心改动】挂起函数等待取色结果
     * UI 组件(如 Renderer) 调用此方法后会挂起，直到用户在画布上点击或取消。
     * * @return 选中的颜色，如果取消则返回 null
     */
    suspend fun awaitColorPick(): Color? {
        // 1. 进入取色模式：通知 Canvas 显示十字准星
        _uiState.update { it.copy(isColorPicking = true) }

        return try {
            // 2. 挂起，等待 handleEvent(TriggerColorPick) 往通道里塞数据
            colorPickChannel.receive()
        } catch (e: Exception) {
            // 协程被取消（如组件销毁、界面切换）
            null
        } finally {
            // 3. 无论成功还是异常退出，都自动恢复 UI 状态
            _uiState.update { it.copy(isColorPicking = false) }
        }
    }
}