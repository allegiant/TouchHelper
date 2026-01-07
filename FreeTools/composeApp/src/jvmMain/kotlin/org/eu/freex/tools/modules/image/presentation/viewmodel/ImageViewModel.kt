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
    }

    fun handleEvent(event: ImageUiEvent) {
        viewModelScope.launch {
            // PreviewFilter 不显示 loading，避免闪烁
            if (event !is PreviewFilter && event !is CancelPreview) {
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
    fun startColorPick(callback: (Color) -> Unit) {
        pendingColorCallback = callback
        _uiState.update { it.copy(isColorPicking = true) }
    }

    fun onColorPicked(color: Color) {
        pendingColorCallback?.invoke(color)
        cancelColorPick()
    }

    fun cancelColorPick() {
        pendingColorCallback = null
        _uiState.update { it.copy(isColorPicking = false) }
    }
}