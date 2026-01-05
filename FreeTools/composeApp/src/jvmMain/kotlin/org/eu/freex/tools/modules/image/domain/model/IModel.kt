package org.eu.freex.tools.modules.image.domain.model

/**
 * 纯净的密封标记接口 (Sealed Marker Interface)
 *
 * 作用：
 * 1. 建立家族关系：告诉编译器 Pipeline 和 Project 都是 "StateComponent" 的一部分。
 * 2. 强制穷举：因为是 sealed，Kotlin 编译器会强制 ImageUiState 处理所有子类。
 * 3. 零污染：没有任何方法，不污染 Model 的业务逻辑。
 */
sealed interface StateComponent