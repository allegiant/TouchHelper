package org.eu.freex.tools.modules.image.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.eu.freex.tools.modules.image.application.ImageProcessingUseCase
import org.eu.freex.tools.modules.image.application.ProjectAssetUseCase
import org.eu.freex.tools.modules.image.domain.model.ImageLayer
import org.eu.freex.tools.modules.image.domain.repository.ProjectRepository
import java.io.File

data class ProjectListUiState(
    val assets: List<ImageLayer> = emptyList(),
    val activeAssetId: String? = null
)

class ProjectListViewModel(
    private val projectRepo: ProjectRepository,
    private val assetUseCase: ProjectAssetUseCase,
    private val processingUseCase: ImageProcessingUseCase
) : ViewModel() {

    // 组合流：监听资源列表变化 + 监听当前选中的图片ID
    val uiState: StateFlow<ProjectListUiState> = combine(
        projectRepo.assets,
        projectRepo.pipeline
    ) { assets, pipeline ->
        ProjectListUiState(
            assets = assets,
            activeAssetId = pipeline?.inputAssetId
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProjectListUiState()
    )

    fun importImage(file: File) {
        viewModelScope.launch {
            // 错误处理交由 UI 层观察或在这里调用 MainViewModel (如果是简单的 Demo，直接 catch)
            assetUseCase.importImage(file)
        }
    }

    fun captureScreen() {
        viewModelScope.launch {
            assetUseCase.captureScreen()
        }
    }

    fun deleteAsset(assetId: String) {
        viewModelScope.launch {
            assetUseCase.removeImage(assetId)
        }
    }

    fun selectAsset(assetId: String) {
        viewModelScope.launch {
            // 切换图片，保留滤镜链
            processingUseCase.activateAsset(assetId)
        }
    }

    fun exportImage(layer: ImageLayer, file: File) {
        viewModelScope.launch {
            assetUseCase.exportImage(layer, file)
        }
    }

    // [新增] 导出当前画布显示的图片（即滤镜处理后的结果）
    fun exportDisplayImage(file: File) {
        val currentDisplay = projectRepo.workspace.value.displayImage
        if (currentDisplay != null) {
            viewModelScope.launch {
                assetUseCase.exportImage(currentDisplay, file)
            }
        }
    }
}