package org.eu.freex.tools.modules.image.presentation.viewmodel

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class EditorCanvasTransform(
    val scale: Float = 1f,
    val pan: Offset = Offset.Zero
)

class EditorCanvasViewModel : ViewModel() {
    private val _transformState = MutableStateFlow(EditorCanvasTransform())
    val transformState: StateFlow<EditorCanvasTransform> = _transformState.asStateFlow()

    fun updateTransform(zoomChange: Float, panChange: Offset) {
        _transformState.update { state ->
            val newScale = (state.scale * zoomChange).coerceIn(0.1f, 10f)
            val newPan = state.pan + panChange
            state.copy(scale = newScale, pan = newPan)
        }
    }


    // 如果需要重置画布位置
    fun resetTransform() {
        _transformState.value = EditorCanvasTransform()
    }
}