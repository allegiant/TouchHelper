package org.eu.freex.tools

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import org.eu.freex.tools.common.model.AppModule
import org.eu.freex.tools.common.EnStrings
import org.eu.freex.tools.common.ZhStrings
import org.eu.freex.tools.common.i18n.ProvideAppStrings
import org.eu.freex.tools.common.theme.AppTheme
import org.eu.freex.tools.common.theme.ThemeMode
import org.koin.compose.koinInject
import java.awt.Component
import java.awt.Container
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.*
import java.io.File

// 组件
import org.eu.freex.tools.modules.image.presentation.ImageWorkbench
import org.eu.freex.tools.modules.image.presentation.PickingWorkbench
import org.eu.freex.tools.modules.image.presentation.features.library.FontManagerPanel
import org.eu.freex.tools.modules.image.presentation.features.recognition.RecognitionScreen
// ViewModels
import org.eu.freex.tools.modules.image.presentation.viewmodel.FontLibraryViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.MainViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.ProjectListViewModel
import org.eu.freex.tools.modules.image.presentation.viewmodel.RecognitionViewModel

@Composable
fun App(window: androidx.compose.ui.awt.ComposeWindow?) {
    val systemLocale = java.util.Locale.getDefault().language
    val strings = if (systemLocale == "zh") ZhStrings else EnStrings

    var currentModule by remember { mutableStateOf(AppModule.IMAGE_PROCESSING) }
    var themeMode by remember { mutableStateOf(ThemeMode.System) }

    // 注入 ViewModels
    val mainViewModel = koinInject<MainViewModel>()
    val projectListViewModel = koinInject<ProjectListViewModel>()
    val fontViewModel = koinInject<FontLibraryViewModel>()
    val recognitionViewModel: RecognitionViewModel = koinInject()

    val fontState by fontViewModel.uiState.collectAsState()
    var showRecognitionScreen by remember { mutableStateOf(false) }

    // [拖拽支持] (直接调用 VM 方法)
    if (window != null) {
        DisposableEffect(window) {
            val dropTarget = object : DropTarget() {
                override fun drop(evt: DropTargetDropEvent) {
                    try {
                        evt.acceptDrop(DnDConstants.ACTION_COPY)
                        val list = evt.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<*>
                        list.firstOrNull()?.let {
                            val file = it as File
                            val name = file.name.lowercase()
                            if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".bmp") || name.endsWith(".webp")) {
                                projectListViewModel.importImage(file)
                            } else if (name.endsWith(".fxproj")) {
                                mainViewModel.loadProject(file)
                            }
                        }
                        evt.dropComplete(true)
                    } catch (e: Exception) {
                        e.printStackTrace(); evt.dropComplete(false)
                    }
                }
            }
            fun attachToAll(component: Component) {
                component.dropTarget = dropTarget
                if (component is Container) {
                    for (child in component.components) attachToAll(child)
                }
            }
            attachToAll(window)
            onDispose { window.dropTarget = null }
        }
    }

    ProvideAppStrings(strings) {
        AppTheme(themeMode = themeMode) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                if (showRecognitionScreen) {
                    // === 1. OCR 识别结果页 ===
                    RecognitionScreen(
                        onBack = { showRecognitionScreen = false }
                    )
                } else {
                    Column(Modifier.fillMaxSize()) {
                        // [修改] TopBar 直接接受函数引用
                        TopBar(
                            currentModule = currentModule,
                            themeMode = themeMode,
                            onThemeChange = { themeMode = it },
                            onModuleChange = { currentModule = it },
                            // 直接绑定 ViewModel 方法
                            onLoadProject = mainViewModel::loadProject,
                            onSaveProject = mainViewModel::saveProject,
                            onExportImage = projectListViewModel::exportDisplayImage,
                            onRecognitionTest = { file ->
                                recognitionViewModel.startRecognition(file)
                                showRecognitionScreen = true
                            }
                        )

                        Box(Modifier.weight(1f)) {
                            when (currentModule) {
                                AppModule.IMAGE_PROCESSING -> {
                                    ImageWorkbench()
                                }

                                AppModule.FONT_MANAGER -> {
                                    // [修改] 直接绑定 FontViewModel 方法
                                    FontManagerPanel(
                                        library = fontState.items,
                                        onDelete = fontViewModel::deleteItem,
                                        onSort = fontViewModel::sortLibrary,
                                        onClear = fontViewModel::clearLibrary,
                                        onExport = fontViewModel::exportLibrary
                                    )
                                }
                                AppModule.PICKING_TOOL -> {
                                    PickingWorkbench()
                                }
                            }
                        }
                    }
                }

            }
        }
    }
}