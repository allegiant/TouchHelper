package org.eu.freex.tools.modules.image.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.eu.freex.tools.modules.image.application.ProjectAssetUseCase
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
}