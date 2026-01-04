// 路径: src/jvmMain/kotlin/org/eu/freex/tools/modules/image/presentation/contract/events/FilterEvents.kt
package org.eu.freex.tools.modules.image.presentation.contract.events

import kotlinx.coroutines.launch
import org.eu.freex.tools.modules.image.domain.model.ImageFilter
import org.eu.freex.tools.modules.image.domain.model.ViewFilter
import org.eu.freex.tools.modules.image.presentation.contract.ImageActionScope
import org.eu.freex.tools.modules.image.presentation.contract.ImageUiEvent
import org.eu.freex.tools.modules.image.presentation.contract.getPrevStepImage
import org.eu.freex.tools.modules.image.presentation.contract.model.DraftState
import kotlin.math.max

// =================================================================================
// 1. 预览与调节 (Drafting)
// =================================================================================

/**
 * 触发预览/参数调节
 * 无论是切换滤镜类型，还是拖动滑块，都调用此事件
 * @param filter 新的滤镜参数
 * @param forceReloadBaseImage 是否强制重新获取输入图 (例如切换步骤后首次调节)
 */
data class PreviewFilter(
    val filter: ImageFilter,
    val forceReloadBaseImage: Boolean = false
) : ImageUiEvent {
    override fun ImageActionScope.execute() {
        // 1. 确定输入图 (Base Image)
        // 【关键修复】逻辑修正：
        // 如果是编辑模式（selectedPipelineIndex > 0），我们想要的是“当前步骤的输入”，也就是“上一步的输出”。
        // 旧逻辑调用 state.getPreviousImageForProcessing() 往往返回的是当前步骤的输出，导致滤镜叠加（如二值化后再次二值化->无效果）。
        val baseImage = if (forceReloadBaseImage || state.pipeline.draft.baseImage == null) {
            val currentIndex = state.pipeline.selectedPipelineIndex
            // 计算输入源的索引：
            // 如果在原图(0)，输入源也是原图(0)用于预览第一步。
            // 如果在步骤N(N>0)，输入源是步骤N-1的结果。
            val inputIndex = max(0, currentIndex - 1)
            // 使用 ImageActionScope.getPrevStepImage (在 ImageActionScope.kt 中定义的扩展方法)
            getPrevStepImage(inputIndex)
        } else {
            state.pipeline.draft.baseImage
        } ?: return

        // 2. 异步计算预览
        scope.launch {
            // 如果切回“原图/无滤镜”模式
            if (filter is ViewFilter) {
                setPipeline {
                    copy(draft = DraftState(activeFilter = filter, previewImage = null, baseImage = baseImage))
                }
                return@launch
            }

            filterService.processSingle(baseImage, filter)
                .onSuccess { previewResult ->
                    setPipeline {
                        copy(
                            draft = DraftState(
                                activeFilter = filter,
                                previewImage = previewResult,
                                baseImage = baseImage
                            )
                        )
                    }
                }
                .onFailure {
                    showToast("预览失败: ${it.message}")
                }
        }
    }
}

// =================================================================================
// 2. 提交动作 (Commit Actions)
// =================================================================================

/**
 * 按钮动作：【应用(新增)】
 * 逻辑：将当前的预览结果作为“新步骤”追加到当前位置之后
 */
object ApplyNewStep : ImageUiEvent {
    override fun ImageActionScope.execute() {
        val draft = state.pipeline.draft
        // 必须要有预览图才能提交，或者是 ViewFilter (但不允许添加 ViewFilter)
        if (draft.activeFilter is ViewFilter) {
            showToast("请先选择一个滤镜效果")
            return
        }
        val resultImage = draft.previewImage ?: return

        val insertIndex = state.pipeline.selectedPipelineIndex
        val currentSteps = state.pipeline.pipelineSteps

        // 截断逻辑：保留插入点之前的步骤，追加新步骤
        val newSteps = currentSteps.take(insertIndex).toMutableList()
        newSteps.add(resultImage)

        setPipeline {
            copy(
                pipelineSteps = newSteps,
                selectedPipelineIndex = newSteps.size, // 焦点移动到最新步骤
                // 提交后重置 Draft
                draft = DraftState()
            )
        }

        // 自动选中新步骤，并预加载它的输入图作为 BaseImage
        launch { handleEvent(SelectPipelineStep(newSteps.size)) }
    }
}

/**
 * 按钮动作：【修改(更新)】
 * 逻辑：更新当前选中的步骤，并级联重算后续步骤
 */
object UpdateCurrentStep : ImageUiEvent {
    override fun ImageActionScope.execute() {
        val selectedIndex = state.pipeline.selectedPipelineIndex
        // 索引 0 是原图，不能修改
        if (selectedIndex <= 0) {
            showToast("原图无法修改，请使用【应用】新增处理步骤")
            return
        }

        val draft = state.pipeline.draft
        if (draft.activeFilter is ViewFilter) {
            showToast("无法将步骤修改为空滤镜")
            return
        }
        val newImageResult = draft.previewImage ?: return

        launch {
            val stepIndexToReplace = selectedIndex - 1
            val currentSteps = state.pipeline.pipelineSteps

            // 1. 获取被修改步骤之后的链条 (Tail)
            val tailSteps = currentSteps.drop(stepIndexToReplace + 1)
            val filtersToReplay = tailSteps.mapNotNull { it.appliedFilter }

            // 2. 级联重算后续步骤
            // 如果没有后续步骤，newImageResult 就是最终结果
            val newTailImages = if (filtersToReplay.isNotEmpty()) {
                filterService.processChain(newImageResult, filtersToReplay).getOrElse {
                    showToast("后续步骤重算失败: ${it.message}")
                    return@launch
                }
            } else {
                emptyList()
            }

            // 3. 组装新列表: [前缀] + [新当前步] + [新后缀]
            val newSteps = currentSteps.take(stepIndexToReplace) + newImageResult + newTailImages

            setPipeline {
                copy(
                    pipelineSteps = newSteps,
                    selectedPipelineIndex = selectedIndex, // 焦点保持不变
                    // 修改完成后，重置 Draft，但为了体验，可以让 Inspector 保持当前参数
                    // 此时 BaseImage 置空，下次动滑块时会自动重新获取
                    draft = DraftState(
                        activeFilter = newImageResult.appliedFilter ?: ViewFilter,
                        previewImage = null,
                        baseImage = null
                    )
                )
            }
            showToast("步骤已更新")
        }
    }
}

// =================================================================================
// 3. 导航与管理
// =================================================================================

/**
 * 选中流水线中的某个步骤
 * 动作：除了移动指针，还需要把该步骤的 Filter 加载到 Draft 中，以便 Inspector 回显
 */
data class SelectPipelineStep(val index: Int) : ImageUiEvent {
    override fun ImageActionScope.execute() {
        val targetFilter = if (index == 0) {
            ViewFilter
        } else {
            state.pipeline.pipelineSteps.getOrNull(index - 1)?.appliedFilter ?: ViewFilter
        }

        // 【关键修复】
        // 切换步骤时，立即准备好该步骤的“输入图”作为 baseImage。
        // 这样当用户拖动滑块时，是基于“输入图”进行计算，而不是基于“当前结果”计算。
        val inputIndex = max(0, index - 1)
        val baseImage = getPrevStepImage(inputIndex)

        setPipeline {
            copy(
                selectedPipelineIndex = index,
                draft = DraftState(
                    activeFilter = targetFilter,
                    previewImage = null,
                    baseImage = baseImage // 预加载 BaseImage
                )
            )
        }
    }
}

data class DeletePipelineStep(val index: Int) : ImageUiEvent {
    override fun ImageActionScope.execute() {
        if (index <= 0) return
        val stepIndexToRemove = index - 1
        val currentSteps = state.pipeline.pipelineSteps

        launch {
            val keptSteps = currentSteps.take(stepIndexToRemove)
            val tailSteps = currentSteps.drop(stepIndexToRemove + 1)
            val filtersToReplay = tailSteps.mapNotNull { it.appliedFilter }

            val baseImage = if (keptSteps.isNotEmpty()) keptSteps.last() else state.project.currentSourceImage
            if (baseImage == null) return@launch

            val recalculatedTail = filterService.processChain(baseImage, filtersToReplay).getOrElse { emptyList() }

            val finalSteps = keptSteps + recalculatedTail

            setPipeline {
                copy(
                    pipelineSteps = finalSteps,
                    selectedPipelineIndex = finalSteps.size,
                    draft = DraftState()
                )
            }
        }
    }
}