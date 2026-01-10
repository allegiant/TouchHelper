package org.eu.freex.tools.modules.image.domain.model

import kotlinx.serialization.Serializable

// 这个类代表了"切割工作"的持久化数据
@Serializable
data class SegmentationProject(
    val config: SegmentationConfig = SegmentationConfig(
        mode = SegmentationMode.FIXED_GRID,
        padding = 0,
        minWidth = 10u, minHeight = 10u,
        startX = 0, startY = 0,
        cellWidth = 32u, cellHeight = 32u,
        colCount = 1u, rowCount = 1u,
        colGap = 0, rowGap = 0,
        splitRows = true, splitCols = true,
        projectionThreshold = 128u
    ),
    // 切割结果 (坐标)
    val results: List<SegmentationRect> = emptyList(),
    // 标注结果 (索引 -> 字符)
    val labels: Map<Int, String> = emptyMap()
)