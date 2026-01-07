package org.eu.freex.tools.modules.image.presentation.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.eu.freex.tools.modules.image.application.WorkspaceUseCase
import org.eu.freex.tools.modules.image.domain.model.ImageFilter
import org.eu.freex.tools.modules.image.domain.model.ImageLayer
import org.eu.freex.tools.modules.image.domain.model.ImageWorkspace
import org.eu.freex.tools.modules.image.domain.model.LayerConfig
import org.eu.freex.tools.modules.image.presentation.core.*

class ImageViewModel(
    private val useCase: WorkspaceUseCase
) : ViewModel() {

    private var workspace = ImageWorkspace()
    private val _uiState = MutableStateFlow(ImageUiState())
    val uiState = _uiState.asStateFlow()

    private data class PreviewRequest(val baseLayer: ImageLayer, val filter: ImageFilter)

    // 使用 CONFLATED 通道解决积压
    private val previewChannel = Channel<PreviewRequest>(Channel.CONFLATED)

    // 内部暂存回调函数 (不暴露给 UI)
    private var pendingColorCallback: ((Color) -> Unit)? = null

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

                            // 2. 【核心修复：防抖动逻辑】
                            // 检查返回的结果类型是否与当前 UI 显示的滤镜类型一致。
                            // 如果一致，说明是同一个滤镜的连续拖动。
                            val resultFilter = resultLayer.activeFilter
                            val currentFilter = currentPreview.activeFilter

                            if (resultFilter != null && currentFilter != null &&
                                resultFilter::class == currentFilter::class
                            ) {

                                // 关键点：我们使用【计算出的新图片】，但保留【当前最新的参数配置】。
                                // 这样即使 resultLayer 是 100ms 前的旧参数请求产生的，
                                // 它也不会把滑块的位置（Config）“拖”回去，只会更新画面。
                                currentState.copy(
                                    previewLayer = resultLayer.copy(config = currentPreview.config)
                                )
                            } else {
                                // 如果类型都不一样了（比如用户极速切换了 Tab），说明结果过期了，或者就是需要切类型
                                // 这种情况下直接使用结果即可
                                currentState.copy(previewLayer = resultLayer)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun handleEvent(event: ImageUiEvent) {
        viewModelScope.launch {
            if (event !is PreviewFilter) _uiState.update { it.copy(isLoading = true) }

            runCatching {
                when (event) {
                    is LoadFile -> {
                        useCase.importAsset(event.file).onSuccess { layer ->
                            // 1. 先把新图加入资源列表
                            var newWorkspace = workspace.copy(assets = workspace.assets + layer)
                            // 2. 然后激活它 (会自动应用当前的滤镜链)
                            useCase.activateAsset(newWorkspace, layer.id)
                                .onSuccess { finalWorkspace ->
                                    workspace = finalWorkspace
                                    _uiState.update { s -> s.copy(previewLayer = null) }
                                }
                                .onFailure {
                                    // 如果激活失败（比如滤镜出错），至少保留资源列表的更新
                                    workspace = newWorkspace
                                    it.printStackTrace()
                                }
                        }
                    }

                    is SelectAsset -> {
                        // 修改：处理 suspend 结果
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
                        useCase.saveWorkspace(event.file, workspace).onFailure {
                            it.printStackTrace() // 如果这里打印了错误，说明保存过程中崩了
                        }
                    }
                    is LoadProject -> {
                        useCase.loadWorkspace(event.file).onSuccess { loadedWorkspace ->
                            workspace = loadedWorkspace
                            _uiState.update { s -> s.copy(previewLayer = null) }
                            refreshUiState() // 必须调用，触发 UI 重绘列表和画布
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
                        // 1. 先把裁剪出来的新图加入到 workspace 的资源列表中
                        val workspaceWithAsset = workspace.copy(assets = workspace.assets + croppedLayer)

                        // 2. 尝试激活这张新图 (复用当前的滤镜链重新计算)
                        useCase.activateAsset(workspaceWithAsset, croppedLayer.id)
                            .onSuccess { finalWorkspace ->
                                // 成功：更新整个工作区，并关闭裁剪框
                                workspace = finalWorkspace
                                _uiState.update { s -> s.copy(cropperLayer = null) }
                            }
                            .onFailure { e ->
                                // 失败：打印错误，但至少保留新图在列表中，并关闭裁剪框
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
                    is PreviewFilter -> {
                        val chain = workspace.pipeline
                        if (chain != null) {
                            val idx = chain.activeIndex

                            // 确定底图逻辑：
                            // 1. 如果选中原图(-1)或第0步，底图是原图
                            // 2. 如果选中第N步，底图是第N-1步的结果
                            val baseLayer = if (idx <= 0) {
                                workspace.assets.find { it.id == chain.inputAssetId }
                            } else {
                                chain.steps.getOrNull(idx - 1)
                            }

                            if (baseLayer?.image != null) {
                                // 1. 【同步更新 UI (乐观更新)】
                                // 立即把 previewLayer 的 Config 更新为用户滑动的最新值。
                                // 这保证了滑块紧跟鼠标，不会有延迟。
                                // 图片暂时复用当前的（避免闪烁）。
                                val currentImage = _uiState.value.previewLayer?.image
                                    ?: chain.getActiveLayer(workspace.assets)?.image
                                    ?: baseLayer.image

                                _uiState.update {
                                    it.copy(
                                        previewLayer = ImageLayer(
                                            name = "Previewing...",
                                            image = currentImage,
                                            config = LayerConfig.Filter(event.filter) // 这里的 filter 是最新的
                                        ),
                                        isLoading = false
                                    )
                                }

                                // 2. 【异步计算】
                                // 放入通道，结果回来时会与上面的 Config 进行合并
                                previewChannel.trySend(PreviewRequest(baseLayer, event.filter))
                                return@launch
                            }
                        }
                    }

                    is ApplyNewStep -> {
                        val p = _uiState.value.previewLayer
                        if (p != null && p.config is LayerConfig.Filter) {
                            useCase.addFilterStep(workspace, p.config.filter).onSuccess {
                                workspace = it
                                _uiState.update { s -> s.copy(previewLayer = null) }
                            }
                        }
                    }

                    is UpdateCurrentStep -> {
                        val p = _uiState.value.previewLayer
                        if (p != null && p.config is LayerConfig.Filter) {
                            useCase.updateFilterStep(workspace, p.config.filter).onSuccess {
                                workspace = it
                                _uiState.update { s -> s.copy(previewLayer = null) }
                            }
                        }
                    }

                    is SelectStep -> {
                        workspace.pipeline?.let {
                            workspace = workspace.copy(pipeline = it.copy(activeIndex = event.index))
                        }
                        _uiState.update { s -> s.copy(previewLayer = null) }
                    }

                    is StartFontMaker -> useCase.startFontGeneration(workspace).onSuccess { workspace = it }
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
                fontGenerator = workspace.fontGenerator
            )
        }
    }

    // --- 动作 ---

    /**
     * 开始取色
     */
    fun startColorPick(callback: (Color) -> Unit) {
        // 1. 存下回调
        pendingColorCallback = callback
        // 2. 更新 State 通知 UI 切换模式
        _uiState.update { it.copy(isColorPicking = true) }
    }

    /**
     * 确认取色 (由 Canvas 调用)
     */
    fun onColorPicked(color: Color) {
        // 1. 执行回调
        pendingColorCallback?.invoke(color)
        // 2. 清理现场
        cancelColorPick()
    }

    /**
     * 取消取色
     */
    fun cancelColorPick() {
        pendingColorCallback = null
        _uiState.update { it.copy(isColorPicking = false) }
    }

}