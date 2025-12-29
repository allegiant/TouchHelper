package org.eu.freex.tools.model
import org.jetbrains.exposed.sql.Table

// 1. 工程元数据表
object ProjectInfoTable : Table("project_info") {
    val id = integer("id").autoIncrement()
    val version = varchar("version", 20).default("1.0")
    val createTime = long("create_time")
    val updateTime = long("update_time")
    override val primaryKey = PrimaryKey(id)
}

// 2. 资源图片表 (只存源图片的路径)
object SourceImagesTable : Table("source_images") {
    val id = integer("id").autoIncrement()
    val path = text("path") // 本地绝对路径
    val orderIndex = integer("order_index") // 排序
    val alias = varchar("alias", 255).nullable() // 图片名
    override val primaryKey = PrimaryKey(id)
}

// 3. 处理流水线表 (存储滤镜步骤和参数)
object PipelineStepsTable : Table("pipeline_steps") {
    val id = integer("id").autoIncrement()
    val type = varchar("type", 50) // Filter 类型: "BINARY", "GRAYSCALE" 等
    val paramsJson = text("params_json") // 参数 Map 的 JSON 字符串
    val orderIndex = integer("order_index") // 执行顺序
    val isEnabled = bool("is_enabled").default(true)
    override val primaryKey = PrimaryKey(id)
}