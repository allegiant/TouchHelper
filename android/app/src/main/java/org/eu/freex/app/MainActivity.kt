package org.eu.freex.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import uniffi.rust_core.PlatformLogger
import java.io.File
import java.util.concurrent.Executors

// 1. 实现 Rust 定义的 Logger 接口
// 这样 Rust 里的 info!/error! 宏就能显示在 Android Logcat 中了
class AndroidLogger : PlatformLogger {
    override fun log(msg: String) {
        Log.i("RustCore", msg)
    }
}
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 使用线程池异步初始化，防止阻塞主线程
        Executors.newSingleThreadExecutor().execute {
            // 1. 部署 server.jar 等资源
            val serverPath = deployServerEnv()

            // 2. 初始化 Rust 核心服务
            initRustService(serverPath)

            // 3. 初始化完成后跳转到 WebView
            runOnUiThread {
                startActivity(Intent(this, WebViewActivity::class.java))
                finish() // 关闭当前 Activity
            }
        }
    }

    private fun deployServerEnv(): String {
        try {
            // 确定 server.jar 路径 (App 私有目录)
            val serverFile = File(filesDir, "server.jar")

            // 每次启动都从 Assets 覆盖，确保是最新的代码
            assets.open("server.jar").use { input ->
                serverFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // 赋予可读可执行权限
            serverFile.setReadable(true, false)
            serverFile.setExecutable(true, false)

            Log.i("TouchHelper", "Server JAR deployed to: ${serverFile.absolutePath}")
            return serverFile.absolutePath

        } catch (e: Exception) {
            Log.e("TouchHelper", "Failed to deploy server environment", e)
            return ""
        }
    }

    private fun initRustService(serverPath: String) {
        try {
            // A. 将 server.jar 路径存入配置，供 Rust 端读取
            if (serverPath.isNotEmpty()) {
                uniffi.rust_core.setConfig("server_path", serverPath)
            }

            // B. 启动服务 (Root 模式)
            // 参数说明:
            // 1. useRoot = true (开启 Root 模式)
            // 2. logger = AndroidLogger() (传入日志回调)
            // 3. service = null (Root 模式不需要 AccessibilityService)
            uniffi.rust_core.initService(true, AndroidLogger(), null)
            Log.i("TouchHelper", "Rust Core Initialized in ROOT mode")

        } catch (e: Exception) {
            Log.e("TouchHelper", "Failed to init Rust Service", e)
        }
    }
}