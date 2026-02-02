package org.eu.freex.tools.modules.image.presentation.viewmodel.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap

/**
 * 实时预览状态 (对应界面右上角的放大镜区域)
 */
data class PreviewState(
    val hasContent: Boolean = false,
    val hoverX: Int = 0,
    val hoverY: Int = 0,
    val hoverColor: Color = Color.Transparent,
    val magnifierBitmap: ImageBitmap? = null, // 局部放大图
    val binaryBitmap: ImageBitmap? = null     // 二值化预览图
)