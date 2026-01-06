package org.eu.freex.tools.common

data class AppStrings(
    val appName: String = "FreeTools",
    val file: String,
    val saveProject: String,
    val loadProject: String,
    val cancel: String,
    val confirm: String,
    val filterPipeline: String,
    val assetList: String,
    val screenshot: String,
    val theme: String,
    val dark: String,
    val light: String
)

val EnStrings = AppStrings(
    file = "File",
    saveProject = "Save Project (.fxproj)",
    loadProject = "Load Project (.fxproj)",
    cancel = "Cancel",
    confirm = "Confirm",
    filterPipeline = "Filter Pipeline",
    assetList = "Assets",
    screenshot = "Screenshot",
    theme = "Theme",
    dark = "Dark",
    light = "Light"
)

val ZhStrings = AppStrings(
    file = "文件",
    saveProject = "保存工程 (.fxproj)",
    loadProject = "载入工程 (.fxproj)",
    cancel = "取消",
    confirm = "确认",
    filterPipeline = "滤镜流水线",
    assetList = "资源列表",
    screenshot = "屏幕截图",
    theme = "主题",
    dark = "深色",
    light = "浅色"
)