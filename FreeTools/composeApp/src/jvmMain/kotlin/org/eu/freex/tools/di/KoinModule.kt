package org.eu.freex.tools.di

import org.eu.freex.tools.modules.image.application.PipelineUseCase
import org.eu.freex.tools.modules.image.application.ProjectUseCase
import org.eu.freex.tools.modules.image.data.repository.ImageRepositoryImpl
import org.eu.freex.tools.modules.image.domain.repository.ImageRepository
import org.eu.freex.tools.modules.image.domain.service.FilterService
import org.eu.freex.tools.modules.image.domain.service.ProjectService
import org.eu.freex.tools.modules.image.domain.service.ResourceService
import org.eu.freex.tools.modules.image.presentation.core.EventDispatcher
import org.eu.freex.tools.modules.image.presentation.core.ImageEventHandler
import org.eu.freex.tools.modules.image.presentation.features.filter.FilterEventHandler
import org.eu.freex.tools.modules.image.presentation.features.pipeline.PipelineEventHandler
import org.eu.freex.tools.modules.image.presentation.features.project.ProjectEventHandler
import org.eu.freex.tools.modules.image.presentation.features.tools.ToolEventHandler
import org.eu.freex.tools.modules.image.presentation.viewmodel.ImageViewModel
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val appDiModule = module {
    // =========================================================================
    // 1. 数据层 (Data Layer)
    // =========================================================================
    // 注册 Repository 实现
    singleOf(::ImageRepositoryImpl) { bind<ImageRepository>() }

    // =========================================================================
    // 2. 领域服务层 (Domain Service Layer) - 【之前漏掉了这一层】
    // =========================================================================
    singleOf(::FilterService)
    singleOf(::ProjectService)
    singleOf(::ResourceService)

    // =========================================================================
    // 3. 应用服务层 (Application Layer / UseCases)
    // =========================================================================
    singleOf(::PipelineUseCase)
    singleOf(::ProjectUseCase)

    // =========================================================================
    // 4. 表现层 - 处理器 (Handlers) - 自动注册的关键
    // =========================================================================
    // 将所有 Handler 都绑定到 ImageEventHandler 接口上
    singleOf(::PipelineEventHandler) { bind<ImageEventHandler>() }
    singleOf(::ProjectEventHandler) { bind<ImageEventHandler>() }
    singleOf(::FilterEventHandler) { bind<ImageEventHandler>() }
    singleOf(::ToolEventHandler) { bind<ImageEventHandler>() }

    // =========================================================================
    // 5. 表现层 - 分发与状态 (Presentation Core)
    // =========================================================================
    // 分发器：Koin 会自动收集上面所有绑定了 ImageEventHandler 的实例注入到 List 中
    single { EventDispatcher(getAll()) }

    // ViewModel：自动注入 Dispatcher 和 UseCases
    singleOf(::ImageViewModel)
}