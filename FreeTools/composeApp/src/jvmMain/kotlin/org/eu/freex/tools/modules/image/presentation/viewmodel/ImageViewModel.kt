package org.eu.freex.tools.modules.image.presentation.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.eu.freex.tools.common.state.BaseEventDispatcher
import org.eu.freex.tools.common.state.BaseViewModel
import org.eu.freex.tools.modules.image.application.WorkspaceUseCase
import org.eu.freex.tools.modules.image.domain.model.ImageFilter
import org.eu.freex.tools.modules.image.domain.model.ImageLayer
import org.eu.freex.tools.modules.image.domain.model.ImageWorkspace
import org.eu.freex.tools.modules.image.presentation.core.*


internal data class PreviewRequest(val baseLayer: ImageLayer, val filter: ImageFilter)
class ImageViewModel(
    internal val useCase: WorkspaceUseCase
) : BaseViewModel<ImageUiEvent, ImageUiState>(
    initialState = ImageUiState(),
    dispatcher = BaseEventDispatcher(emptyList()) // 传空，因为我们用扩展函数自己分发
) {

    private var workspace = ImageWorkspace()

    private val colorPickChannel = Channel<Color>(Channel.RENDEZVOUS)
    private val pointPickChannel = Channel<IntOffset>(Channel.RENDEZVOUS)
    private val previewChannel = Channel<PreviewRequest>(Channel.CONFLATED)

    init {
        setupReactiveSegmentation()

        viewModelScope.launch {
            previewChannel.consumeEach { request ->
                runCatching {
                    val resultLayer = useCase.calculatePreview(request.baseLayer, request.filter)
                    if (resultLayer != null) applyPreviewResult(resultLayer)
                }.onFailure { it.printStackTrace() }
            }
        }
    }


    // --- Main Event Router ---
    override fun handleEvent(event: ImageUiEvent) {
        viewModelScope.launch {
            if (shouldShowLoading(event)) {
                updateUiState { it.copy(isLoading = true) }
            }
            runCatching {
                when (event) {
                    is AssetEvent -> handleAssetEvent(event)
                    is InteractionEvent -> handleInteractionEvent(event)
                    is PipelineEvent -> handlePipelineEvent(event)
                    is SegmentationEvent -> handleSegmentEvent(event)
                    is FontLibraryEvent -> handleFontLibraryEvent(event)
                }
            }.onFailure {
                it.printStackTrace()
                sendEffect(it.message ?: "Unknown Error") // 使用基类的 Toast 发送能力
            }
            updateUiState {   it.copy(isLoading = false) }
        }
    }

    // --- ViewModelContext Implementation ---

    internal fun getWorkspaceSnapshot(): ImageWorkspace = workspace
    internal fun updateWorkspace(transform: (ImageWorkspace) -> ImageWorkspace) {
        workspace = transform(workspace)
        refreshUiState()
    }

    internal fun updateUiState(transform: (ImageUiState) -> ImageUiState) {
        _uiState.update(transform)
    }

    private fun refreshUiState() {
        _uiState.update {
            it.copy(
                assets = workspace.assets,
                activeChain = workspace.pipeline,
                segmentationProject = workspace.segmentation,
                fontLibrary = workspace.fontLibrary
            )
        }
    }

    // --- Exposed Helpers (For UI Components) ---


    suspend fun awaitColorPick(): Color? {
        // 直接更新 UI 状态
        updateUiState { it.copy(pickingType = PickingType.COLOR) }
        return try {
            colorPickChannel.receive()
        } catch (e: Exception) {
            null
        } finally {
            updateUiState { it.copy(pickingType = PickingType.NONE) }
        }
    }

    suspend fun awaitPointPick(): IntOffset? {
        updateUiState { it.copy(pickingType = PickingType.POINT) }
        return try {
            pointPickChannel.receive()
        } catch (e: Exception) {
            null
        } finally {
            updateUiState { it.copy(pickingType = PickingType.NONE) }
        }
    }

    // --- Internal Helpers for Extension Functions ---

    // [新增] 供扩展函数发送选取的颜色
    internal suspend fun sendColorPick(color: Color) {
        colorPickChannel.send(color)
    }

    // [新增] 供扩展函数发送选取的坐标
    internal suspend fun sendPointPick(point: IntOffset) {
        pointPickChannel.send(point)
    }

    // --- Utility ---

    private fun shouldShowLoading(event: ImageUiEvent): Boolean {
        // 白名单机制：如果事件是交互型的或瞬时型的，不需要显示全屏 Loading
        return when (event) {
            is PreviewFilter,
            is TriggerColorPick,
            is TriggerPointPick,
            is CancelPick,
            is SwitchTab,
            is UpdateSegmentationConfig,
            is SelectChar,
            is SubmitLabelAndNext,
            is StopLabeling -> false

            else -> true
        }
    }

    // --- Internal Helpers ---

    // [新增] 供扩展函数发送预览请求
    internal fun sendPreviewRequest(baseLayer: ImageLayer, filter: ImageFilter) {
        previewChannel.trySend(PreviewRequest(baseLayer, filter))
    }

    // [新增] 处理预览结果 (这是 consumer 调用的逻辑，放在主类里比较合适，也可以通过扩展函数拆分但没必要)
    private fun applyPreviewResult(resultLayer: ImageLayer) {
        _uiState.update { currentState ->
            val currentPreview = currentState.previewLayer ?: return@update currentState

            // 校验：防止结果回来时用户已经切换了滤镜类型
            val resultFilter = resultLayer.activeFilter
            val currentFilter = currentPreview.activeFilter

            if (resultFilter != null && currentFilter != null &&
                resultFilter::class == currentFilter::class
            ) {
                // 保留 UI 上的 Slider 配置
                currentState.copy(previewLayer = resultLayer.copy(config = currentPreview.config))
            } else {
                currentState.copy(previewLayer = resultLayer)
            }
        }
    }
}