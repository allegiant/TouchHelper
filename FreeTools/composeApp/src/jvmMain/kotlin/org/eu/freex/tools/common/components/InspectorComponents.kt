package org.eu.freex.tools.common.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * [InspectorComponents.kt]
 * 右侧属性面板 (Inspector) 的通用 UI 组件库。
 * 旨在统一滤镜、切割、抓抓等面板的视觉风格。
 */

// ==========================================
// 1. 面板与区块标题
// ==========================================

/**
 * 面板区块标题
 * @param title 标题文本
 * @param trailingContent 右侧的操作按钮插槽 (如：重置、清空)
 */
@Composable
fun InspectorSectionHeader(
    modifier: Modifier = Modifier,
    title: String,
    trailingContent: @Composable () -> Unit = {}
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            // 右侧操作区
            Row(verticalAlignment = Alignment.CenterVertically) {
                trailingContent()
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(8.dp))
    }
}

// ==========================================
// 2. 属性行布局
// ==========================================

/**
 * 通用的“标签 + 控件”属性行
 * @param label 左侧标签文本
 * @param description 可选的工具提示文本 (使用 HelpTooltip)
 * @param content 右侧的控件内容 (如 Slider, TextField)
 */
@Composable
fun InspectorPropertyRow(
    label: String,
    description: String? = null,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧标签区
        Row(
            modifier = Modifier.width(90.dp), // 固定宽度以保证对齐
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!description.isNullOrEmpty()) {
                HelpTooltip(description = description)
            }
        }

        // 右侧控件区
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}

// ==========================================
// 3. 颜色相关组件
// ==========================================

/**
 * 颜色预览块 (支持透明度网格背景)
 * @param color 要显示的颜色
 * @param size 色块大小
 * @param onClick 点击回调 (可选)
 */
@Composable
fun ColorSwatch(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(4.dp)
    val borderStroke = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

    Surface(
        modifier = modifier
            .size(size)
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
        border = borderStroke,
        color = Color.White // 底色设为白，用于绘制棋盘格
    ) {
        Box {
            // 1. 绘制透明度棋盘格背景
            TransparencyGrid(modifier = Modifier.matchParentSize())

            // 2. 覆盖实际颜色
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(color)
            )
        }
    }
}

/**
 * 绘制灰白棋盘格 (用于展示透明度)
 */
@Composable
private fun TransparencyGrid(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val squareSize = 4.dp.toPx()
        val columns = (size.width / squareSize).toInt() + 1
        val rows = (size.height / squareSize).toInt() + 1

        for (row in 0 until rows) {
            for (col in 0 until columns) {
                if ((row + col) % 2 == 0) {
                    drawRect(
                        color = Color.LightGray.copy(alpha = 0.5f),
                        topLeft = Offset(col * squareSize, row * squareSize),
                        size = Size(squareSize, squareSize)
                    )
                }
            }
        }
    }
}

// ==========================================
// 4. 按钮与操作
// ==========================================

/**
 * 紧凑型图标按钮 (用于列表项中的删除/编辑操作)
 */
@Composable
fun CompactIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(24.dp) // 比默认的 48dp 小很多
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(16.dp),
            tint = tint
        )
    }
}