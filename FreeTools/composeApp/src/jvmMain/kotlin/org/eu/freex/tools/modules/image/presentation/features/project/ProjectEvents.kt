package org.eu.freex.tools.modules.image.presentation.features.project

import org.eu.freex.tools.modules.image.presentation.core.ProjectEvent
import java.awt.image.BufferedImage
import java.io.File

// 实现 ProjectEvent 接口
data class LoadFile(val file: File) : ProjectEvent
data class SelectSourceImage(val index: Int) : ProjectEvent
data class RemoveSourceImage(val index: Int) : ProjectEvent
data class SaveProject(val file: File) : ProjectEvent
data class LoadProject(val file: File) : ProjectEvent
data class ExportImage(val file: File) : ProjectEvent
object StartScreenCapture : ProjectEvent
data class ConfirmScreenCrop(val image: BufferedImage) : ProjectEvent