package org.eu.freex.tools.modules.image.presentation.viewmodel

// 引入 Mapper 扩展 (假设您已按之前建议创建了这些文件)
// import org.eu.freex.tools.modules.image.domain.mapper.toRust
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.eu.freex.tools.modules.image.application.RecognitionUseCase
import org.eu.freex.tools.modules.image.domain.model.LayerConfig
import org.eu.freex.tools.modules.image.domain.model.RecognitionResult
import org.eu.freex.tools.modules.image.domain.repository.ProjectRepository
import java.io.File

// 定义 UI 状态
data class RecognitionUiState(
    val isLoading: Boolean = false,
    val selectedImage: File? = null,           // 当前选中的测试图片
    val results: List<RecognitionResult> = emptyList(), // 识别结果列表
    val error: String? = null,                 // 错误信息
    val timeCostMs: Long = 0                   // 耗时统计 (毫秒)
)

class RecognitionViewModel(
    private val projectRepo: ProjectRepository,
    private val recognitionUseCase: RecognitionUseCase
) {
    // 使用 StateFlow 管理状态 (也可以配合 Jetpack ViewModel 使用)
    private val _uiState = MutableStateFlow(RecognitionUiState())
    val uiState = _uiState.asStateFlow()

    // 协程作用域 (如果是 Android ViewModel 则使用 viewModelScope)
    private val scope = CoroutineScope(Dispatchers.Main)
    private var job: Job? = null

    /**
     * 用户选择了新的测试图片
     */
    fun onImageSelected(file: File) {
        _uiState.update {
            it.copy(
                selectedImage = file,
                results = emptyList(),
                error = null,
                timeCostMs = 0
            )
        }
    }

    /**
     * 执行识别测试
     */
    fun runTest() {
        val currentState = _uiState.value
        val imageFile = currentState.selectedImage ?: return

        if (job?.isActive == true) return

        job = scope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val startTime = System.currentTimeMillis()

            try {
                // 1. 获取当前 Workspace 数据
                val workspace = projectRepo.workspace.value

                // 2. 获取切割配置 (UI State -> Rust Config)
                // ⚠️ 注意：这里需要调用您之前定义的 toRust() 扩展函数
                // 如果 workspace.segmentation?.config 为空，说明还没配置过切割，可以提示错误或使用默认值
                val uiSegConfig = workspace.segmentation?.config
                    ?: throw IllegalStateException("请先在切割面板配置参数")

                // 2. 直接获取 Kotlin 滤镜列表
                val uiFilters = workspace.pipeline?.steps
                    ?.mapNotNull { step ->
                        (step.config as? LayerConfig.Filter)?.filter
                    } ?: emptyList()

                // 4. 调用 UseCase 执行核心逻辑
                // UseCase 内部会先跑滤镜，再切割，再二值化，最后匹配字库
                val resultList = recognitionUseCase.recognize(
                    imageFile = imageFile,
                    config = uiSegConfig,
                    filters = uiFilters,
                    minConfidence = 0.7f // 可以做成 UI 可配置参数
                ).getOrThrow()

                // 5. 更新成功状态
                val cost = System.currentTimeMillis() - startTime
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        results = resultList,
                        timeCostMs = cost
                    )
                }

            } catch (e: Exception) {
                // 6. 处理错误
                e.printStackTrace()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "识别过程发生未知错误"
                    )
                }
            }
        }
    }
}