package org.eu.freex.tools.modules.image.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.eu.freex.tools.modules.image.domain.model.FontLibItem
import org.eu.freex.tools.modules.image.domain.model.ImageLayer
import org.eu.freex.tools.modules.image.domain.model.ImageWorkspace
import org.eu.freex.tools.modules.image.domain.model.Pipeline
import org.eu.freex.tools.modules.image.domain.model.SegmentationProject
import java.io.File

// 假设 Asset 是定义在 ImageWorkspace 同级或子级的模型，如果引用不到请自行导入
// import org.eu.freex.tools.modules.image.domain.model.Asset

interface ProjectRepository {
    // 1. 全局真理源 (Source of Truth)
    val workspace: StateFlow<ImageWorkspace>

    // 2. 细粒度流 (用于性能优化)
    // 只有当 pipeline 真的变了，订阅者才会收到通知
    val pipeline: Flow<Pipeline?>

    // 只有当切割方案变了，订阅者才会收到通知
    val segmentation: Flow<SegmentationProject?>

    // 只有当资源列表变了，订阅者才会收到通知
    val assets: Flow<List<ImageLayer>>
    // 字库
    val fontLibrary: Flow<List<FontLibItem>>

    // 3. 原子更新操作
    // 这是修改状态的唯一入口，线程安全
    fun updateWorkspace(transform: (ImageWorkspace) -> ImageWorkspace)

    // 4. (可选) 这是一个重置/加载项目的挂起函数
    suspend fun resetWorkspace(newWorkspace: ImageWorkspace)
}