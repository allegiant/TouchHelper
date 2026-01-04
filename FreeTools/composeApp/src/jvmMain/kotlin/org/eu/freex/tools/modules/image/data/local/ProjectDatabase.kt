package org.eu.freex.tools.modules.image.data.local

import kotlinx.serialization.json.Json
import org.eu.freex.tools.modules.image.data.local.entities.PipelineStepsTable
import org.eu.freex.tools.modules.image.data.local.entities.ProjectInfoTable
import org.eu.freex.tools.modules.image.data.local.entities.SourceImagesTable
import org.eu.freex.tools.modules.image.domain.model.ImageFilter
import org.eu.freex.tools.modules.image.domain.model.ViewFilter
import org.eu.freex.tools.modules.image.domain.model.WorkImage
import org.eu.freex.tools.modules.image.domain.model.type
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.io.File

object ProjectDatabase {
    private val json = Json {
        ignoreUnknownKeys = true
        // 开启多态支持（默认开启，但显式配置更安全）
        useArrayPolymorphism = false
        classDiscriminator = "type" // 指定类型字段名为 'type'
    }

    // 连接数据库
    private fun connect(dbFile: File): Database {
        // 使用 sqlite-jdbc 连接
        return Database.Companion.connect("jdbc:sqlite:${dbFile.absolutePath}", "org.sqlite.JDBC")
    }

    /**
     * 保存工程
     * @param file 目标 .fxproj 文件
     * @param sourceImages 源图片列表
     * @param currentFilter 当前滤镜
     */
    fun saveProject(
        file: File,
        sourceImages: List<WorkImage>,
        currentFilters: List<ImageFilter>?,
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
            currentFilters?.forEach { filter ->
                if (filter !is ViewFilter) {
                    val jsonString = json.encodeToString(ImageFilter.serializer(), filter)
                    PipelineStepsTable.insert {
                        it[type] = filter.type
                        it[paramsJson] = jsonString
                        it[orderIndex] = 0
                        it[isEnabled] = true
                    }
                }
            }
        }
    }

    /**
     * 加载工程
     * 返回: (图片路径列表, 步骤列表)
     */
    fun loadProject(file: File): Pair<List<String>, List<ImageFilter>> {
        val db = connect(file)
        val paths = mutableListOf<String>()
        val filters = mutableListOf<ImageFilter>()

        transaction(db) {
            if (!file.exists()) return@transaction

            // 读取图片路径
            SourceImagesTable.selectAll().orderBy(SourceImagesTable.orderIndex).forEach {
                paths.add(it[SourceImagesTable.path])
            }

            // 读取步骤并反序列化为 AppFilter
            PipelineStepsTable.selectAll().orderBy(PipelineStepsTable.orderIndex).forEach {
                val jsonString = it[PipelineStepsTable.paramsJson]
                val loadedFilter: ImageFilter =
                    json.decodeFromString(ImageFilter.serializer(), jsonString)
                filters.add(loadedFilter)
            }
        }
        return Pair(paths, filters)
    }
}