package org.eu.freex.tools.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.eu.freex.tools.modules.image.application.FontLibraryUseCase
import org.eu.freex.tools.modules.image.application.ImageProcessingUseCase
import org.eu.freex.tools.modules.image.application.ProjectAssetUseCase
import org.eu.freex.tools.modules.image.application.RecognitionUseCase
import org.eu.freex.tools.modules.image.application.SegmentationUseCase
import org.eu.freex.tools.modules.image.data.repository.LayerRepositoryImpl
import org.eu.freex.tools.modules.image.data.repository.ProjectRepositoryImpl
import org.eu.freex.tools.modules.image.domain.repository.LayerRepository
import org.eu.freex.tools.modules.image.domain.repository.ProjectRepository
import org.eu.freex.tools.modules.image.presentation.viewmodel.EditorCanvasViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.FontLibraryViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.MainViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.PickingToolViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.PipelineViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.ProjectListViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.RecognitionViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.SegmentationViewModel
import org.eu.freex.tools.platform.DesktopScreenCaptureService
import org.eu.freex.tools.platform.ScreenCaptureService
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appDiModule = module {

    single<ScreenCaptureService> { DesktopScreenCaptureService() }
    // =================================================================================
    // 1. Data Layer (基础设施与数据仓库)
    // =================================================================================

    // 提供一个全局的 CoroutineScope 给 ProjectRepository 使用
    // 使用 SupervisorJob 确保子任务失败不会导致整个 Scope 取消
    single<CoroutineScope> {
        CoroutineScope(Dispatchers.Default + SupervisorJob())
    }

    // LayerRepository: 负责底层图像算法 (OpenCV/Rust/IO)
    // 注意：如果 LayerRepositoryImpl 的构造函数需要参数（如 Context），请在这里填入 get()
    single<LayerRepository> { LayerRepositoryImpl(get()) }

    // ProjectRepository: 负责全局状态管理 (Single Source of Truth)
    single<ProjectRepository> { ProjectRepositoryImpl(get()) }


    // =================================================================================
    // 2. Domain Layer (UseCases - 业务逻辑)
    // =================================================================================

    single { ProjectAssetUseCase(get(), get()) }
    single { ImageProcessingUseCase(get(), get()) }
    single { SegmentationUseCase(get(), get()) }
    single { FontLibraryUseCase(get()) }
    single { RecognitionUseCase(get(), get()) }


    // =================================================================================
    // 3. Presentation Layer (ViewModels - UI 状态适配)
    // =================================================================================

    // 全局协调者：Loading, Error, Toast
    single{ MainViewModel(get(), get()) }

    // 左侧资源列表：导入、导出、管理图片
    single{ ProjectListViewModel(get(), get(), get()) }

    // 右侧滤镜流水线：滤镜链管理、预览
    single{ PipelineViewModel(get(), get()) }

    // 智能切割：参数配置、运行算法、标注
    single{ SegmentationViewModel(get(), get()) }

    // 字库管理：预览、导出
    single{ FontLibraryViewModel(get(), get()) }

    // 画布交互：显示最终结果、处理点击/裁剪
    single{ EditorCanvasViewModel(get(), get()) }
    single { PickingToolViewModel() }
    single { RecognitionViewModel(get(), get()) }
}