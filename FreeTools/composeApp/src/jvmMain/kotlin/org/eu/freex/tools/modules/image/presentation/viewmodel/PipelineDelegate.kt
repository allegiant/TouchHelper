package org.eu.freex.tools.modules.image.presentation.viewmodel

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import org.eu.freex.tools.modules.image.domain.model.*
import org.eu.freex.tools.modules.image.presentation.core.*

class PipelineDelegate(private val context: ViewModelContext) {

    private data class PreviewRequest(val baseLayer: ImageLayer, val filter: ImageFilter)
    // 使用 CONFLATED 保证如果预览处理太慢，只会处理最新的请求，丢弃旧的
    private val previewChannel = Channel<PreviewRequest>(Channel.CONFLATED)

    init {
        context.scope.launch {
            previewChannel.consumeEach { request ->
                runCatching {
                    val resultLayer = context.useCase.calculatePreview(request.baseLayer, request.filter)
                    if (resultLayer != null) applyPreviewResult(resultLayer)
                }.onFailure { it.printStackTrace() }
            }
        }
    }

    suspend fun handle(event: PipelineEvent) {
        when (event) {
            is PreviewFilter -> triggerPreview(event.filter)
            is CancelPreview -> context.updateUiState { it.copy(previewLayer = null) }
            is ApplyFilterStep -> applyFilterStep(event.filter)
            is UpdateFilterStep -> updateFilterStep(event.filter)
            is SelectStep -> selectStep(event.index)
            is RemoveStep -> removeStep(event.index)
        }
    }

    private fun triggerPreview(filter: ImageFilter) {
        val workspace = context.getWorkspaceSnapshot()
        val chain = workspace.pipeline ?: return
        val idx = chain.activeIndex

        // 查找输入源 (Base Layer)
        val baseLayer = if (idx <= 0) {
            workspace.assets.find { it.id == chain.inputAssetId }
        } else {
            chain.steps.getOrNull(idx - 1)
        }

        if (baseLayer?.image != null) {
            // 乐观更新 UI：先显示一个 loading 或旧图，防止闪烁
            val currentImage = context.uiState.value.previewLayer?.image
                ?: chain.getActiveLayer(workspace.assets)?.image
                ?: baseLayer.image

            context.updateUiState {
                it.copy(
                    previewLayer = ImageLayer(
                        name = "Previewing...",
                        image = currentImage,
                        config = LayerConfig.Filter(filter)
                    ),
                    isLoading = false
                )
            }
            previewChannel.trySend(PreviewRequest(baseLayer, filter))
        }
    }

    private fun applyPreviewResult(resultLayer: ImageLayer) {
        context.updateUiState { currentState ->
            val currentPreview = currentState.previewLayer ?: return@updateUiState currentState

            // 校验：防止结果回来时用户已经切换了滤镜类型
            val resultFilter = resultLayer.activeFilter
            val currentFilter = currentPreview.activeFilter

            if (resultFilter != null && currentFilter != null &&
                resultFilter::class == currentFilter::class) {
                // 保留 UI 上的 Slider 配置 (Config)，只更新 Image 数据
                currentState.copy(previewLayer = resultLayer.copy(config = currentPreview.config))
            } else {
                currentState.copy(previewLayer = resultLayer)
            }
        }
    }

    private suspend fun applyFilterStep(filter: ImageFilter) {
        // [修复] 显式命名 newWorkspace，避免隐式 it 被 updateWorkspace 的上下文遮蔽
        context.useCase.addFilterStep(context.getWorkspaceSnapshot(), filter).onSuccess { newWorkspace ->
            context.updateWorkspace { newWorkspace } // 直接替换
            context.updateUiState { s -> s.copy(previewLayer = null) }
        }
    }

    private suspend fun updateFilterStep(filter: ImageFilter) {
        // [修复] 同上
        context.useCase.updateFilterStep(context.getWorkspaceSnapshot(), filter).onSuccess { newWorkspace ->
            context.updateWorkspace { newWorkspace }
            context.updateUiState { s -> s.copy(previewLayer = null) }
        }
    }

    private fun selectStep(index: Int) {
        // [修复] 利用 Receiver (this) 直接访问 ImageWorkspace 属性
        context.updateWorkspace {
            // 这里的 this 就是当前的 Workspace
            pipeline?.let { currentChain ->
                copy(pipeline = currentChain.copy(activeIndex = index))
            } ?: this
        }
        context.updateUiState { s -> s.copy(previewLayer = null) }
    }

    private suspend fun removeStep(index: Int) {
        // 调用 UseCase 执行删除和重算逻辑
        context.useCase.removeStep(context.getWorkspaceSnapshot(), index)
            .onSuccess { newWorkspace ->
                // 更新 Workspace
                context.updateWorkspace { newWorkspace }
                // 退出可能存在的预览状态
                context.updateUiState { s -> s.copy(previewLayer = null) }
            }
            .onFailure { it.printStackTrace() }
    }
}