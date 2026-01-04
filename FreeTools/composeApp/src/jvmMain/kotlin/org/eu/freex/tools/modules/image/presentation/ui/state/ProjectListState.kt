package org.eu.freex.tools.modules.image.presentation.ui.state

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.eu.freex.tools.modules.image.domain.model.WorkImage
import org.eu.freex.tools.modules.image.presentation.contract.ImageUiEvent
import org.eu.freex.tools.modules.image.presentation.contract.events.LoadFile
import org.eu.freex.tools.modules.image.presentation.contract.events.RemoveSourceImage
import org.eu.freex.tools.modules.image.presentation.contract.events.SelectSourceImage
import org.eu.freex.tools.modules.image.presentation.contract.events.StartScreenCapture
import org.eu.freex.tools.modules.image.presentation.contract.model.ProjectSession
import java.io.File

/**
 * 项目资源管理器状态持有者
 * 职责：
 * 1. 代理 ProjectState 中的数据访问
 * 2. 封装列表滚动等纯 UI 状态
 * 3. 封装业务操作指令，避免 UI 层传递大量 Lambda
 */
@Stable
class ProjectExplorerState(
    private val projectSession: ProjectSession,
    private val onEvent: (ImageUiEvent) -> Unit,
    val listState: LazyListState,
    private val scope: CoroutineScope
) {
    // --- 数据代理 (Data Proxy) ---
    val images: List<WorkImage>
        get() = projectSession.sourceImages

    val selectedIndex: Int
        get() = projectSession.selectedSourceIndex

    // --- 行为封装 (Actions) ---
    fun select(index: Int) {
        if (index != selectedIndex) {
            onEvent(SelectSourceImage(index))
        }
    }

    fun remove(index: Int) {
        onEvent(RemoveSourceImage(index))
    }

    fun importFile(file: File) {
        onEvent(LoadFile(file))
        // 导入后自动滚动到底部 (可选体验优化)
        scope.launch {
            // 简单延迟一下等待数据更新，或者使用 LaunchedEffect 监听 count 变化
            try { listState.animateScrollToItem(images.size) } catch (_: Exception) {}
        }
    }

    fun startScreenCapture() {
        onEvent(StartScreenCapture)
    }
}

@Composable
fun rememberProjectExplorerState(
    projectSession: ProjectSession,
    onEvent: (ImageUiEvent) -> Unit
): ProjectExplorerState {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    return remember(projectSession, onEvent, listState, scope) {
        ProjectExplorerState(projectSession, onEvent, listState, scope)
    }
}