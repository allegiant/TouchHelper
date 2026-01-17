package org.eu.freex.tools.modules.image.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.eu.freex.tools.modules.image.application.FontLibraryUseCase
import org.eu.freex.tools.modules.image.domain.model.FontLibItem
import org.eu.freex.tools.modules.image.domain.model.SegmentationRect
import org.eu.freex.tools.modules.image.domain.repository.ProjectRepository
import java.io.File

data class FontLibraryUiState(
    val items: List<FontLibItem> = emptyList()
)

class FontLibraryViewModel(
    private val projectRepo: ProjectRepository,
    private val fontLibraryUseCase: FontLibraryUseCase
) : ViewModel() {

    val uiState: StateFlow<FontLibraryUiState> = projectRepo.fontLibrary
        .map { FontLibraryUiState(items = it) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            FontLibraryUiState()
        )

    fun addToLibrary(items: List<Pair<SegmentationRect, String>>) {
        viewModelScope.launch {
            fontLibraryUseCase.addBatchToLibrary(items)
        }
    }

    fun exportLibrary(file: File) {
        viewModelScope.launch {
            fontLibraryUseCase.exportLibrary(file)
        }
    }


    fun deleteItem(id: String) {
        projectRepo.updateWorkspace { ws ->
            ws.copy(fontLibrary = ws.fontLibrary.filter { it.id != id })
        }
    }

    fun clearLibrary() {
        projectRepo.updateWorkspace { ws ->
            ws.copy(fontLibrary = emptyList())
        }
    }

    fun sortLibrary() {
        projectRepo.updateWorkspace { ws ->
            ws.copy(fontLibrary = ws.fontLibrary.sortedBy { it.charName })
        }
    }
}