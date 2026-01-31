package org.eu.freex.tools.modules.image.presentation.features.editor.registry

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntSize
import org.eu.freex.tools.common.model.PickingToolState
import org.eu.freex.tools.modules.image.presentation.features.editor.layers.ColorPickerLayer
import org.eu.freex.tools.modules.image.presentation.features.editor.layers.PointPickerLayer
import java.awt.image.BufferedImage
import kotlin.reflect.KClass

object ToolUIRegistry {

    // 1. 定义具体的渲染器实现 (Object 单例)

    private object ColorPickerRenderer : ToolRenderer {
        @Composable
        override fun Content(image: BufferedImage) {
            ColorPickerLayer(sourceImage = image)
        }
    }

    private object PointPickerRenderer : ToolRenderer {
        @Composable
        override fun Content(image: BufferedImage) {
            // 参数适配：Layer 需要 IntSize，这里从 Image 获取
            PointPickerLayer(imageSize = IntSize(image.width, image.height))
        }
    }

    private object EmptyRenderer : ToolRenderer {
        @Composable
        override fun Content(image: BufferedImage) {
            // 什么都不做
        }
    }

    // 2. 注册表映射 (Key: Class -> Value: Renderer)
    private val renderers = mapOf<KClass<out PickingToolState>, ToolRenderer>(
        PickingToolState.ColorPicker::class to ColorPickerRenderer,
        PickingToolState.PointPicker::class to PointPickerRenderer
    )

    // 3. 对外暴露的获取方法
    fun getRenderer(state: PickingToolState): ToolRenderer {
        return renderers[state::class] ?: EmptyRenderer
    }
}