// 路径: src/jvmMain/kotlin/org/eu/freex/tools/modules/image/presentation/contract/events/FileEvents.kt
package org.eu.freex.tools.modules.image.presentation.contract.events

import org.eu.freex.tools.modules.image.domain.model.WorkImage
import org.eu.freex.tools.modules.image.presentation.contract.ImageActionScope
import org.eu.freex.tools.modules.image.presentation.contract.ImageUiEvent
import org.eu.freex.tools.modules.image.presentation.contract.state.DraftState
import java.io.File

// =================================================================================
// 1. 单文件操作 (Single File Operations)
// =================================================================================

class LoadFile(val file: File) : ImageUiEvent {
    override fun ImageActionScope.execute() {
        launch {
            resourceProcessor.loadFile(file)
                .onSuccess { newImage ->
                    // 纯 Project 操作
                    setProject {
                        copy(
                            sourceImages = sourceImages + newImage,
                            selectedSourceIndex = sourceImages.size // 选中新图 (旧size即新图index)
                        )
                    }
                    // ViewModel 监听到 selectedSourceIndex 变化，会自动驱动流水线
                }
                .onFailure { showToast("加载文件失败: ${it.message}") }
        }
    }
}

data class SelectSourceImage(val index: Int) : ImageUiEvent {
    override fun ImageActionScope.execute() {
        if (index !in state.project.sourceImages.indices) return
        setProject { copy(selectedSourceIndex = index) }
    }
}

data class RemoveSourceImage(val index: Int) : ImageUiEvent {
    override fun ImageActionScope.execute() {
        val currentImages = state.project.sourceImages
        if (index !in currentImages.indices) return

        val newImages = currentImages.toMutableList().apply { removeAt(index) }
        val newIndex = when {
            newImages.isEmpty() -> -1
            index >= newImages.size -> newImages.size - 1
            else -> index
        }

        setProject {
            copy(
                sourceImages = newImages,
                selectedSourceIndex = newIndex
            )
        }
    }
}

/**
 * 导出当前显示的图片
 */
data class ExportImage(val file: File) : ImageUiEvent {
    override fun ImageActionScope.execute() {
        // 获取当前显示的最终效果图 (activeDisplayImage)
        val imageToSave = state.activeDisplayImage?.bufferedImage
        if (imageToSave == null) {
            showToast("当前没有可导出的图片")
            return
        }
        // TODO: 实现具体的保存逻辑，例如调用 ImageUtils.save(imageToSave, file)
        showToast("暂未实现导出功能")
    }
}

// =================================================================================
// 2. 工程管理 (Project Management)
// =================================================================================

/**
 * 保存工程
 */
data class SaveProject(val file: File) : ImageUiEvent {
    override fun ImageActionScope.execute() {
        // 获取所有源图和当前流水线步骤
        val sourceImages = state.project.sourceImages
        val pipelineSteps = state.pipeline.pipelineSteps

        if (sourceImages.isEmpty()) {
            showToast("没有源文件，无法保存工程")
            return
        }

        launch {
            // 调用 ProjectProcessor
            projectProcessor.saveProject(file, sourceImages, pipelineSteps)
                .onSuccess {
                    showToast("工程保存成功")
                }
                .onFailure {
                    showToast("保存失败: ${it.message}")
                }
        }
    }
}

/**
 * 加载工程
 * 逻辑：载入源图和滤镜配方 -> 更新 State -> 依赖 ViewModel 自动触发重算
 */
data class LoadProject(val file: File) : ImageUiEvent {
    override fun ImageActionScope.execute() {
        launch {
            projectProcessor.loadProject(file)
                .onSuccess { result ->
                    // 1. 构造“空壳”流水线步骤 (只包含滤镜参数)
                    // 此时还没有运行算法，我们用源图做占位符，打上 Filter 标签
                    // ViewModel 的 syncPipeline 会根据 appliedFilter 重新计算真正的图像
                    val placeholderImage = result.sourceImages.firstOrNull() ?: return@onSuccess

                    val restoredSteps = result.filters.map { filter ->
                        WorkImage(
                            bufferedImage = placeholderImage.bufferedImage, // 占位，马上会被重算覆盖
                            appliedFilter = filter,
                            label = "待计算...",
                            name = ""
                        )
                    }

                    // 2. 一次性更新所有状态
                    setState {
                        copy(
                            project = project.copy(
                                sourceImages = result.sourceImages,
                                selectedSourceIndex = 0 // 默认选中第一张
                            ),
                            pipeline = pipeline.copy(
                                pipelineSteps = restoredSteps,
                                selectedPipelineIndex = restoredSteps.size,
                                // 【修复】使用 draft = DraftState() 替代已移除的 currentImage = null
                                // 确保加载新工程时，右侧编辑面板和预览状态被重置
                                draft = DraftState()
                            ),
                            segmentation = segmentation.copy(activeRects = emptyList())
                        )
                    }

                    // 3. 状态更新后，ViewModel 的 distinctUntilChanged 会捕获 sourceImage 变化
                    // 或者因为 pipelineSteps 已经有了数据，syncPipeline 逻辑会接管后续的重算工作
                    showToast("工程加载成功")
                }
                .onFailure {
                    showToast("加载工程失败: ${it.message}")
                }
        }
    }
}

// =================================================================================
// 3. 截图 (Screen Capture)
// =================================================================================

object StartScreenCapture : ImageUiEvent {
    override fun ImageActionScope.execute() {
        launch {
            resourceProcessor.captureScreen()
                .onSuccess { capture ->
                    setUi {
                        copy(
                            fullScreenCapture = capture.bufferedImage,
                            isScreenCropperVisible = true
                        )
                    }
                }
                .onFailure { showToast("截图失败: ${it.message}") }
        }
    }
}

data class ConfirmScreenCrop(val image: java.awt.image.BufferedImage) : ImageUiEvent {
    override fun ImageActionScope.execute() {
        launch {
            val newWorkImage = WorkImage(
                bufferedImage = image,
                name = "Capture_${System.currentTimeMillis()}"
            )
            setProject {
                copy(
                    sourceImages = sourceImages + newWorkImage,
                    selectedSourceIndex = sourceImages.size
                )
            }
            setUi { copy(isScreenCropperVisible = false) }
        }
    }
}