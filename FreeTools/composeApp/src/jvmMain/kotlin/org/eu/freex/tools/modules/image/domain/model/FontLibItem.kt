package org.eu.freex.tools.modules.image.domain.model

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.UUID

@Serializable // <--- 必须加这个
data class FontLibItem(
    val id: String = UUID.randomUUID().toString(),
    val charName: String,
    val width: Int,
    val height: Int,
    val binaryData: String, // 持久化的核心数据

    // 核心修改：加上 @Transient，不仅让 Json 忽略它，也意味着反序列化时它默认为 null
    @Transient
    val displayBitmap: ImageBitmap? = null
)