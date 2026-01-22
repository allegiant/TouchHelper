package org.eu.freex.tools.modules.image.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.eu.freex.tools.modules.image.application.ProjectAssetUseCase
import org.eu.freex.tools.modules.image.application.TsCodeGeneratorUseCase
import org.eu.freex.tools.modules.image.domain.repository.ProjectRepository
import java.io.File

data class MainUiState(
    val isLoading: Boolean = false,
    val loadingMessage: String = "",
    val toastMessage: String? = null,
    val errorMessage: String? = null
)

class MainViewModel(
    private val projectRepo: ProjectRepository,
    private val assetUseCase: ProjectAssetUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()

    // --- 全局操作 ---

    fun loadProject(file: File) {
        runSafe("Loading Project...") {
            assetUseCase.loadProject(file).getOrThrow()
        }
    }

    fun saveProject(file: File) {
        runSafe("Saving Project...") {
            assetUseCase.saveProject(file).getOrThrow()
            showToast("Project saved successfully")
        }
    }

    // --- 状态控制 ---

    fun showLoading(message: String = "Processing...") {
        _uiState.update { it.copy(isLoading = true, loadingMessage = message) }
    }

    fun hideLoading() {
        _uiState.update { it.copy(isLoading = false) }
    }

    fun showToast(message: String) {
        _uiState.update { it.copy(toastMessage = message) }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun showError(error: String) {
        _uiState.update { it.copy(errorMessage = error) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * 安全执行挂起函数，自动处理 Loading 和 Error
     */
    fun runSafe(loadingMsg: String? = null, action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                if (loadingMsg != null) showLoading(loadingMsg)
                action()
            } catch (e: Exception) {
                e.printStackTrace()
                showError(e.message ?: "Unknown Error")
            } finally {
                if (loadingMsg != null) hideLoading()
            }
        }
    }

    fun generateScript(): String {
        // 1. 从 Repository 的 Source of Truth (StateFlow) 获取当前快照
        val currentWorkspace = projectRepo.workspace.value

        // 2. 获取 Pipeline
        val pipeline = currentWorkspace.pipeline

        // 3. 获取切割配置
        // Workspace 中应该包含 segmentation (SegmentationProject?)
        val segmentationProject = currentWorkspace.segmentation
        val segConfig = segmentationProject?.config

        // 4. 获取并转换字库 [修复点]
        // ProjectRepository 中没有 currentLibrary，我们要从 workspace 中拿 List
        val fontList = currentWorkspace.fontLibrary // 假设 workspace 中的字段名叫 fontLibrary

        // 将 List<FontLibItem> 转换为 Map<String, String> (字符 -> 特征)
        val fontLib = fontList.associate { item ->
            // 假设 FontLibItem 的字段是 character 和 feature
            // 如果您的字段名不同 (例如 char, code)，请在这里调整
            item.charName to item.binaryData
        }

        // 5. 调用生成器
        return TsCodeGeneratorUseCase.generate(
            pipeline = pipeline,
            segConfig = segConfig,
            fontLib = fontLib,
            minConf = 0.8f // 这个阈值也可以做成 UI 可配置的参数
        )
    }
}