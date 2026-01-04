package org.eu.freex.tools.di

import org.eu.freex.tools.common.AppWindowManager
import org.eu.freex.tools.modules.image.data.repository.ImageRepositoryImpl
import org.eu.freex.tools.modules.image.domain.repository.ImageRepository
import org.eu.freex.tools.modules.image.presentation.viewmodel.ImageViewModel
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val appDiModule = module {
    // 1. 全局窗口管理 (单例) 【新增】
    singleOf(::AppWindowManager)
    // 1. 数据源 (单例)
    //singleOf(::RustDataSource)

    // 2. Repository (单例，绑定接口)
    // 方式 A: 明确写出构造逻辑
    // single<ImageRepository> { ImageRepositoryImpl(get()) }

    // 方式 B: 使用 DSL (推荐) - 将 ImageRepositoryImpl 绑定到 ImageRepository 接口
    singleOf(::ImageRepositoryImpl) { bind<ImageRepository>() }

    // 3. ViewModel (工厂模式: 每次注入创建新实例)
    // 注意: ImageViewModel 需要修改构造函数以接收 Repository
    factoryOf(::ImageViewModel)
}