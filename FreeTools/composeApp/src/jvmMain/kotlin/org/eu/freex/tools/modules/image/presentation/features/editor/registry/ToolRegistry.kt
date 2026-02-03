package org.eu.freex.tools.modules.image.presentation.features.editor.registry

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntSize
import org.eu.freex.tools.common.model.PickEvent
import org.eu.freex.tools.common.model.PickingToolState
import org.eu.freex.tools.common.utils.toHexString
import org.eu.freex.tools.modules.image.presentation.features.editor.layers.ColorPickerLayer
import org.eu.freex.tools.modules.image.presentation.features.editor.layers.PointPickerLayer
import org.eu.freex.tools.modules.image.presentation.features.editor.layers.RegionPickerLayer
import java.awt.image.BufferedImage
import kotlin.reflect.KClass

/**
 * [ToolRegistry]
 * 纯 UI 工厂
 * * 职责：根据 State 返回对应的 Composable，并负责把 Layer 的回调“翻译”成 PickEvent。
 */
object ToolRegistry {

    private val renderers = mutableMapOf<KClass<out PickingToolState>, ToolRenderer>()

    // 空对象模式，防止空指针
    private object EmptyRenderer : ToolRenderer {
        @Composable
        override fun Content(image: BufferedImage, onEvent: (PickEvent) -> Unit) {
        }
    }

    init {
        // === 1. ColorPicker (取色器) ===
        register<PickingToolState.ColorPicker> { image, onEvent ->
            ColorPickerLayer(
                sourceImage = image,
                // [适配]：Layer 传出的原子数据 -> 包装成 PickEvent 发送
                // 注意：ColorPickerLayer 需要修改为接收 onPick 回调，而不是注入 VM
                onPick = { x, y, color ->
                    onEvent(
                        PickEvent.ColorPicked(
                            x = x,
                            y = y,
                            color = color,
                            hex = color.toHexString()
                        )
                    )
                }
            )
        }

        // === 2. PointPicker (取点器) ===
        register<PickingToolState.PointPicker> { image, onEvent ->
            PointPickerLayer(
                imageSize = IntSize(image.width, image.height),

                // 2. 处理点击回调
                onPick = { x, y ->
                    val color = org.eu.freex.tools.common.utils.ImageUtils.getPixelColor(image, x, y)

                    onEvent(
                        PickEvent.ColorPicked(
                            x = x,
                            y = y,
                            color = color,
                            hex = color.toHexString()
                        )
                    )
                }
            )
        }

        // === 3. RegionPicker (区域选择) ===
        register<PickingToolState.RegionPicker> { image, onEvent ->
            RegionPickerLayer(
                sourceImage = image,
                // [适配]：Layer 裁剪出一张图 -> 包装成 Event 发出去
                onCrop = { croppedImage ->
                    onEvent(PickEvent.RegionPicked(croppedImage))
                }
            )
        }
    }

    // --- 核心 API ---

    fun getRenderer(state: PickingToolState): ToolRenderer {
        return renderers[state::class] ?: EmptyRenderer
    }

    // --- 辅助注册函数 ---

    /**
     * 注册工具的工厂函数
     * @param S 工具状态类型
     * @param factory 构建 UI 的 Lambda。参数为: (图片, 事件发送器)
     */
    private inline fun <reified S : PickingToolState> register(
        noinline factory: @Composable (BufferedImage, (PickEvent) -> Unit) -> Unit
    ) {
        renderers[S::class] = ToolRenderer { image, onEvent ->
            factory(image, onEvent)
        }
    }
}