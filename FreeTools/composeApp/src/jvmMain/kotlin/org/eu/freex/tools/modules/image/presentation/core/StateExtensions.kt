package org.eu.freex.tools.modules.image.presentation.core

import org.eu.freex.tools.modules.image.domain.model.ImageEntity

// ... ImageUiState data class 定义保持不变 ...

// ✅ 新增：Presentation 层专属扩展
// 作用：将任意 StateComponent 更新回 State
// 关键字 infix 允许去掉点和括号：pipeline.update(...) into state
infix fun ImageEntity.commitTo(state: ImageUiState): ImageUiState {
    return state.update(this)
}