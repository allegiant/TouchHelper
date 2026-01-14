package org.eu.freex.tools.di

import org.eu.freex.tools.modules.image.application.WorkspaceUseCase
import org.eu.freex.tools.modules.image.data.repository.LayerRepositoryImpl
import org.eu.freex.tools.modules.image.domain.repository.LayerRepository
import org.eu.freex.tools.modules.image.presentation.viewmodel.ImageViewModel
import org.eu.freex.tools.platform.DesktopScreenCaptureService
import org.eu.freex.tools.platform.ScreenCaptureService
import org.koin.dsl.module

val appDiModule = module {
    single<ScreenCaptureService> { DesktopScreenCaptureService() }
    // 1. Infrastructure
    single<LayerRepository> { LayerRepositoryImpl(get()) }

    // 2. Application
    single { WorkspaceUseCase(get()) }

    // 3. Presentation
    single { ImageViewModel(get()) }
}