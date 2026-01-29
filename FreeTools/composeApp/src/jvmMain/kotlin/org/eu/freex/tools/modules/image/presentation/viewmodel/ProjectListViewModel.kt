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
        val isEmptyAssets = uiState.value.assets.isEmpty()
        viewModelScope.launch {
            assetUseCase.importImage(file)
                .onSuccess { newLayer ->
                    if (isEmptyAssets) {
                        selectAsset(newLayer.id)
                    }
                }
        }
    }

    fun captureScreen() {
        val isEmptyAssets = uiState.value.assets.isEmpty()
        viewModelScope.launch {
            assetUseCase.captureScreen()
                .onSuccess { newLayer ->
                    if (isEmptyAssets) {
                        selectAsset(newLayer.id)
                    }
                }
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

    // 导出当前画布显示的图片（即滤镜处理后的结果）
    fun exportDisplayImage(file: File) {
        val currentDisplay = projectRepo.workspace.value.displayImage
        if (currentDisplay != null) {
            viewModelScope.launch {
                assetUseCase.exportImage(currentDisplay, file)
            }
        }
    }
}