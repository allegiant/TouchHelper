package org.eu.freex.tools.modules.image.data.repository


import kotlinx.serialization.json.Json
import org.eu.freex.tools.model.PipelineStepsTable
import org.eu.freex.tools.model.ProjectInfoTable
import org.eu.freex.tools.model.SourceImagesTable
import org.eu.freex.tools.model.WorkImage
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import uniffi.touch_core.ImageFilter
import java.io.File

object ProjectDatabase {
    private val json = Json { ignoreUnknownKeys = true }

    // 连接数据库
    private fun connect(dbFile: File): Database {
        // 使用 sqlite-jdbc 连接
        return Database.connect("jdbc:sqlite:${dbFile.absolutePath}", "org.sqlite.JDBC")
    }

    /**
     * 保存工程
     * @param file 目标 .fxproj 文件
     * @param sourceImages 源图片列表
     * @param filters 当前应用的滤镜链 (注意：这里需要您在 ViewModel 里维护一个 List<ImageFilter> 或者 List<Step>)
     * @param filterParams 滤镜参数 Map (Key是滤镜类型，Value是参数对象)
     */
    fun saveProject(
        file: File,
        sourceImages: List<WorkImage>,
        // 假设我们要保存当前的滤镜配置。
        // 由于您的 ViewModel 目前似乎是单步操作，我们这里暂时只演示保存“当前选中的滤镜及其参数”作为一步。
        // 如果您有完整的 filterChain，请传入 List。
        currentFilter: ImageFilter?,
        currentParams: Any? // ViewModel 中的 filterParams
    ) {
        val db = connect(file)

        transaction(db) {
            // 建表
            SchemaUtils.create(ProjectInfoTable, SourceImagesTable, PipelineStepsTable)

            // 清空旧数据 (全量覆盖模式)
            SourceImagesTable.deleteAll()
            PipelineStepsTable.deleteAll()

            // 写入元数据
            if (ProjectInfoTable.selectAll().count() == 0L) {
                ProjectInfoTable.insert {
                    it[version] = "1.0"
                    it[createTime] = System.currentTimeMillis()
                    it[updateTime] = System.currentTimeMillis()
                }
            } else {
                ProjectInfoTable.update { it[updateTime] = System.currentTimeMillis() }
            }

            // 写入源图片
            sourceImages.forEachIndexed { index, img ->
                // 只保存有路径的图片 (从文件加载的)
                val filePath = img.path ?: return@forEachIndexed
                SourceImagesTable.insert {
                    it[path] = filePath
                    it[orderIndex] = index
                    it[alias] = img.name
                }
            }

            // 写入滤镜步骤 (示例：将当前配置存为步骤 0)
            if (currentFilter != null && currentParams != null) {
                val typeStr = getFilterTypeString(currentFilter)
                // 简单序列化参数对象为 String (实际项目中建议用 kotlinx.serialization 序列化 params 对象)
                val paramsStr = currentParams.toString()

                PipelineStepsTable.insert {
                    it[type] = typeStr
                    it[paramsJson] = paramsStr
                    it[orderIndex] = 0
                    it[isEnabled] = true
                }
            }
        }
    }

    /**
     * 加载工程
     * 返回: (图片路径列表, 步骤列表)
     */
    fun loadProject(file: File): Pair<List<String>, List<LoadedStep>> {
        val db = connect(file)
        val paths = mutableListOf<String>()
        val steps = mutableListOf<LoadedStep>()

        transaction(db) {
            if (!file.exists()) return@transaction

            // 读取图片路径
            SourceImagesTable.selectAll().orderBy(SourceImagesTable.orderIndex).forEach {
                paths.add(it[SourceImagesTable.path])
            }

            // 读取步骤
            PipelineStepsTable.selectAll().orderBy(PipelineStepsTable.orderIndex).forEach {
                steps.add(LoadedStep(
                    type = it[PipelineStepsTable.type],
                    paramsJson = it[PipelineStepsTable.paramsJson]
                ))
            }
        }
        return Pair(paths, steps)
    }

    data class LoadedStep(val type: String, val paramsJson: String)

    private fun getFilterTypeString(filter: ImageFilter): String {
        return when (filter) {
            is ImageFilter.BlackWhite -> "BINARY"
            is ImageFilter.Color -> "COLOR"
            is ImageFilter.Common -> "COMMON"
            else -> "UNKNOWN"
        }
    }
}