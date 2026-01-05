package org.eu.freex.tools.modules.image.presentation.core


/**
 * 基础事件接口 (纯标记)
 */
sealed interface ImageUiEvent

// --- 事件分类标记 (Handler 路由依据) ---

/**
 * 流水线事件 (由 PipelineEventHandler 处理)
 */
interface PipelineEvent : ImageUiEvent

/**
 * 工程/文件事件 (由 ProjectEventHandler 处理)
 */
interface ProjectEvent : ImageUiEvent

/**
 * 滤镜/参数调节事件 (由 FilterEventHandler 处理)
 */
interface FilterEvent : ImageUiEvent

/**
 * 工具/通用UI事件 (由 ToolEventHandler 处理)
 */
interface ToolEvent : ImageUiEvent