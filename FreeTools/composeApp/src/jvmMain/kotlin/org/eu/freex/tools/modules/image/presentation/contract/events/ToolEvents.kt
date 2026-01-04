package org.eu.freex.tools.modules.image.presentation.contract.events

import androidx.compose.ui.geometry.Rect
import org.eu.freex.tools.common.utils.ImageUtils
import org.eu.freex.tools.modules.image.domain.model.Segmentation
import org.eu.freex.tools.modules.image.domain.model.AutoSegmentation
import org.eu.freex.tools.modules.image.domain.model.GridParams
import org.eu.freex.tools.modules.image.domain.model.GridSegmentation
import org.eu.freex.tools.modules.image.presentation.contract.ImageActionScope
import org.eu.freex.tools.modules.image.presentation.contract.ImageUiEvent

// =================================================================================
// 1. 分割与识别 (Segmentation)
// =================================================================================

/**
 * 执行分割
 * 逻辑：获取当前“看到”的图片 (activeDisplayImage)，根据当前的分割策略 (自动/网格) 进行处理。
 */
object PerformSegmentation : ImageUiEvent {
    override fun ImageActionScope.execute() {
        // 【适配】使用根状态的 activeDisplayImage，确保处理的是当前用户看到的画面（包含滤镜效果）
        val source = state.activeDisplayImage ?: return

        val strategy: Segmentation = if (state.segmentation.isGridMode) {
            GridSegmentation(state.segmentation.gridParams)
        } else {
            AutoSegmentation(emptyList()) // 这里可以扩展传入手动指定的 rects
        }

        launch {
            segmentationService.segment(source, strategy)
                .onSuccess { result ->
                    setSegmentation {
                        copy(
                            activeRects = result.rects,
                            segmentationResults = result.subImages,
                        )
                    }
                    showToast("识别到 ${result.rects.size} 个区域")
                }
                .onFailure {
                    showToast("分割失败: ${it.message}")
                }
        }
    }
}

/**
 * 更新网格参数
 */
data class UpdateGridParams(val params: GridParams) : ImageUiEvent {
    override fun ImageActionScope.execute() = setSegmentation { copy(gridParams = params) }
}

/**
 * 切换分割模式 (网格/自动)
 */
data class ToggleGridMode(val isGrid: Boolean) : ImageUiEvent {
    override fun ImageActionScope.execute() = setSegmentation { copy(isGridMode = isGrid) }
}

// =================================================================================
// 2. 界面交互 (UI Interaction)
// =================================================================================

/**
 * 切换右侧面板 Tab
 */
data class ChangePanelTab(val index: Int) : ImageUiEvent {
    override fun ImageActionScope.execute() {
        // 【修复】原代码直接赋值了旧状态，现已修正为使用传入的 index
        setUi { copy(rightPanelTabIndex = index) }
    }
}

/**
 * 关闭所有弹窗
 */
object DismissDialogs : ImageUiEvent {
    override fun ImageActionScope.execute() {
        setUi {
            copy(
                isScreenCropperVisible = false,
                isMappingDialogVisible = false,
                fullScreenCapture = null,
                mappingBitmap = null
            )
        }
    }
}

// =================================================================================
// 3. 字符映射 (Char Mapping)
// =================================================================================

/**
 * 打开映射弹窗
 * 逻辑：从当前画面中裁剪出指定区域，显示在弹窗中供用户输入对应字符
 */
data class OpenMappingDialog(val rect: Rect) : ImageUiEvent {
    override fun ImageActionScope.execute() {
        // 【适配】同样使用 activeDisplayImage，确保裁剪的是用户当前看到的图
        val sourceBitmap = state.activeDisplayImage?.bufferedImage ?: return

        // 裁剪出对应的小图
        val crop = ImageUtils.cropImage(sourceBitmap, rect)

        setUi {
            copy(
                isMappingDialogVisible = true,
                mappingBitmap = crop
            )
        }
    }
}

/**
 * 确认映射
 * 逻辑：(示例) 打印日志或保存映射关系
 */
data class ConfirmMapping(val char: String) : ImageUiEvent {
    override fun ImageActionScope.execute() {
        showToast("已建立映射: $char")
        // 关闭弹窗
        setUi { copy(isMappingDialogVisible = false, mappingBitmap = null) }

        // TODO: 实际业务中，这里应该调用 repository 保存 OCR 训练数据或映射表
    }
}