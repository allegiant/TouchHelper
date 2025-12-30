package org.eu.freex.tools.modules.image.domain.model


// 引入 Rust 生成的顶层函数
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import uniffi.touch_core.Rect
import uniffi.touch_core.scanComponents as rustScanComponents

/**
 * 切割策略的顶层抽象
 */
@Serializable
sealed interface AppSegmentation {
    val name: String

    // 核心行为：输入像素，输出切割区域 (Rect)
    fun segment(pixels: ByteArray, width: Int, height: Int): List<Rect>
}

/**
 * 策略 1：网格切割
 */
@Serializable
@SerialName("GRID")
data class GridSegmentation(
    val params: GridParams
) : AppSegmentation {
    override val name = "网格切割"

    override fun segment(pixels: ByteArray, width: Int, height: Int): List<Rect> {
        // 调用 Rust，isGridMode = true
        return rustScanComponents(
            pixels = pixels,
            width = width,
            height = height,
            rules = emptyList(), // 网格模式下规则通常为空
            isGridMode = true,
            gridRows = params.rowCount,
            gridCols = params.colCount
            // 注意：如果 Rust 还需要 x, y, w, h 来截取区域，
            // 需要先 crop 像素或者传递 offsets，这里假设是对传入的 pixels 全图处理
        )
    }
}

/**
 * 策略 2：自动组件识别 (Auto/Color Rules)
 */
@Serializable
@SerialName("AUTO")
data class AutoSegmentation(
    // 如果 ColorRule 无法序列化，这里可能需要用 DTO，暂且假设为空列表或可序列化
    val rules: List<String> = emptyList() // 简化演示，实际根据 ColorRule 调整
) : AppSegmentation {
    override val name = "自动识别"

    override fun segment(pixels: ByteArray, width: Int, height: Int): List<Rect> {
        // 调用 Rust，isGridMode = false
        return rustScanComponents(
            pixels = pixels,
            width = width,
            height = height,
            rules = emptyList(), // TODO: 将 this.rules 转换为 Rust 的 ColorRule
            isGridMode = false,
            gridRows = null,
            gridCols = null
        )
    }
}