package org.eu.freex.tools.modules.image.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.eu.freex.tools.modules.image.domain.model.FontLibItem
import org.eu.freex.tools.modules.image.domain.model.ImageLayer
import org.eu.freex.tools.modules.image.domain.model.ImageWorkspace
import org.eu.freex.tools.modules.image.domain.model.Pipeline
import org.eu.freex.tools.modules.image.domain.model.SegmentationProject
import org.eu.freex.tools.modules.image.domain.repository.ProjectRepository

class ProjectRepositoryImpl(
    private val scope: CoroutineScope
) : ProjectRepository {

    private val _workspace = MutableStateFlow(ImageWorkspace())
    override val workspace = _workspace.asStateFlow()

    override val pipeline: Flow<Pipeline?> = _workspace
        .map { it.pipeline }
        .distinctUntilChanged()

    override val segmentation: Flow<SegmentationProject?> = _workspace
        .map { it.segmentation }
        .distinctUntilChanged()

    override val assets: Flow<List<ImageLayer>> = _workspace
        .map { it.assets }
        .distinctUntilChanged()

    // [新增] 实现字库流
    override val fontLibrary: Flow<List<FontLibItem>> = _workspace
        .map { it.fontLibrary }
        .distinctUntilChanged()

    override fun updateWorkspace(transform: (ImageWorkspace) -> ImageWorkspace) {
        // MutableStateFlow.update 是线程安全的原子操作
        _workspace.update(transform)
    }

    override suspend fun resetWorkspace(newWorkspace: ImageWorkspace) {
        // 直接发射新状态，覆盖当前所有数据
        _workspace.emit(newWorkspace)
    }
}