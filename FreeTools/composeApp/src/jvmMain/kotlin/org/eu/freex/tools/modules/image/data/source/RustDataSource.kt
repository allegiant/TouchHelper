package org.eu.freex.tools.modules.image.data.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
// 假设生成的 Uniffi 绑定函数在顶层或某个对象中，这里直接调用
// 如果您的包名不同，请根据实际生成的 Uniffi 代码调整 import
import uniffi.touch_core.ColorRule
import uniffi.touch_core.Rect
import uniffi.touch_core.scanComponents as rustScanComponents

/**
 * Rust 数据源 - 负责调用底层 Uniffi 生成的绑定代码
 */
class RustDataSource {

    /**
     * 扫描组件 / 切割识别 (传递原始像素)
     */
    suspend fun scanComponents(
        pixels: ByteArray,
        width: Int,
        height: Int,
        rules: List<ColorRule>,
        isGridMode: Boolean,
        gridRows: Int?,
        gridCols: Int?
    ): List<Rect> = withContext(Dispatchers.Default) {
        // 【修正】直接传递 ByteArray
        rustScanComponents(
            pixels = pixels,
            width = width,
            height = height,
            rules = rules,
            isGridMode = isGridMode,
            gridRows = gridRows,
            gridCols = gridCols
        )
    }
}