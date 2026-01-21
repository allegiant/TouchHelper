package org.eu.freex.tools.modules.image.domain.mapper

// 引入 UI 层定义的模型 (别名以防混淆)
import org.eu.freex.tools.modules.image.domain.model.SegmentationConfig as UiConfig
import org.eu.freex.tools.modules.image.domain.model.SegmentationMode as UiMode

// 引入 Rust 层生成的模型
import uniffi.touch_core.SegmentationConfig as RustConfig
import uniffi.touch_core.SegmentationMode as RustMode

/**
 * 将 UI 层的切割配置转换为 Rust 层的配置对象
 */
fun UiConfig.toRust(): RustConfig {
    return RustConfig(
        // 1. 枚举转换
        mode = when (this.mode) {
            UiMode.FIXED_GRID -> RustMode.FIXED_GRID
            UiMode.PROJECTION -> RustMode.PROJECTION
            UiMode.CONNECTED_COMP -> RustMode.CONNECTED_COMP
        },

        // 2. 数值转换
        // 注意：您的 SharedValues.kt 中使用了 UInt/UByte，
        // 而 Rust 生成的 Kotlin 代码通常也是 UInt/UByte (如果 uniffi 映射正确)
        // 如果编译报错类型不匹配，请根据提示改为 .toInt() 或 .toUInt()

        padding = this.padding, // Int -> i32 (直接兼容)

        minWidth = this.minWidth, // UInt -> u32 (直接兼容)
        minHeight = this.minHeight,
        maxWidth = this.maxWidth,
        maxHeight = this.maxHeight,

        mergeDistance = this.mergeDistance,

        // --- FixedGrid 参数 ---
        startX = this.startX,
        startY = this.startY,
        cellWidth = this.cellWidth,
        cellHeight = this.cellHeight,
        colCount = this.colCount,
        rowCount = this.rowCount,
        colGap = this.colGap,
        rowGap = this.rowGap,

        // --- Projection 参数 ---
        splitRows = this.splitRows,
        splitCols = this.splitCols,

        // UByte -> u8 (通常直接兼容，如果不兼容请尝试 .toUByte() 或 .toByte())
        projectionThreshold = this.projectionThreshold
    )
}