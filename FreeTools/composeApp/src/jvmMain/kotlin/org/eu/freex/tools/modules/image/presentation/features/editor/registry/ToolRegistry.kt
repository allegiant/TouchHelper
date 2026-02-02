/* Path: composeApp/src/jvmMain/kotlin/org/eu/freex/tools/modules/image/presentation/features/editor/registry/ToolRegistry.kt */
package org.eu.freex.tools.modules.image.presentation.features.editor.registry

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntSize
import org.eu.freex.tools.common.model.PickEvent
import org.eu.freex.tools.common.model.PickingToolState
import org.eu.freex.tools.modules.image.presentation.features.editor.layers.ColorPickerLayer
import org.eu.freex.tools.modules.image.presentation.features.editor.layers.PointPickerLayer
import org.eu.freex.tools.modules.image.presentation.features.editor.layers.RegionPickerLayer
import org.eu.freex.tools.modules.image.presentation.viewmodel.EditorCanvasViewModel
import java.awt.image.BufferedImage
import kotlin.reflect.KClass

/**
 * [ToolRegistry]
 * 极简版工具注册表
 * 职责：
 * 1. UI 渲染 (怎么长)
 * 2. 逻辑处理 (怎么做)
 */
object ToolRegistry {

    // 内部存储
    private val renderers = mutableMapOf<KClass<out PickingToolState>, ToolRenderer>()
    private val eventHandlers = mutableMapOf<KClass<out PickEvent>, (Any, EditorCanvasViewModel) -> Unit>()

    private object EmptyRenderer : ToolRenderer {
        @Composable override fun Content(image: BufferedImage) {}
    }

    init {
        // === 1. 配置 ColorPicker (取色器) ===
        register<PickingToolState.ColorPicker, PickEvent.ColorPicked>(
            // UI
            rendererContent = { image -> ColorPickerLayer(sourceImage = image) },

            // Logic: 取色 -> 退出 (所有界面通用)
            onEvent = { event, vm ->
                vm.pickColor(event.color)
                vm.setActiveTool(PickingToolState.None)
            }
        )

        // === 2. 配置 PointPicker (取点器) ===
        register<PickingToolState.PointPicker, PickEvent.PointPicked>(
            // UI
            rendererContent = { image -> PointPickerLayer(imageSize = IntSize(image.width, image.height)) },

            // Logic: 取点 -> 退出 (所有界面通用)
            onEvent = { event, vm ->
                vm.pickPoint(event.x, event.y)
                vm.setActiveTool(PickingToolState.None)
            }
        )

        register<PickingToolState.RegionPicker, PickEvent.RegionPicked>(
            // UI: 直接调用 Composable 函数
            rendererContent = { image -> RegionPickerLayer(sourceImage = image) },

            // Logic:
            // 因为 RegionPickerLayer 内部直接调用了 PickingToolViewModel，
            // 这里的 onEvent 回调可能不会被触发（取决于你怎么设计事件流）。
            // 如果你希望保持架构一致性，也可以在这里处理“退出工具”的逻辑。
            onEvent = { event, vm ->
                // 如果需要，可以在这里重置工具状态
                vm.setActiveTool(PickingToolState.None)
            }
        )
    }

    // --- 核心方法 ---

    fun getRenderer(state: PickingToolState): ToolRenderer {
        return renderers[state::class] ?: EmptyRenderer
    }

    /**
     * 处理事件 (不再需要 Context 参数)
     */
    fun handleEvent(event: PickEvent, vm: EditorCanvasViewModel) {
        val handler = eventHandlers[event::class]
        handler?.invoke(event, vm)
    }

    // --- 辅助注册函数 ---
    private inline fun <reified S : PickingToolState, reified E : PickEvent> register(
        noinline rendererContent: @Composable (BufferedImage) -> Unit,
        noinline onEvent: (E, EditorCanvasViewModel) -> Unit // 只有一个回调了
    ) {
        renderers[S::class] = ToolRenderer { image -> rendererContent(image) }
        eventHandlers[E::class] = { event, vm -> onEvent(event as E, vm) }
    }
}