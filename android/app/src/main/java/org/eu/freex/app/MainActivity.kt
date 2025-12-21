package org.eu.freex.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import uniffi.rust_core.PlatformLogger
import java.io.File
import java.util.concurrent.Executors
import android.provider.Settings


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

        // "config" 是文件名，"use_root" 是 key，false 是默认值
        val useRootMode = getSharedPreferences("config", MODE_PRIVATE)
            .getBoolean("use_root", false)
        Log.i("TouchHelper", "启动模式: ${if (useRootMode) "Root" else "无障碍"}")

        // 检查模式前提条件
        if (!useRootMode && MacroAccessibilityService.instance == null) {
            // 如果是无障碍模式，但服务没开，跳转去开启
            Toast.makeText(this, "请开启无障碍服务以运行脚本", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            finish() // 暂时结束，等用户开启后再进
            return
        }

        // 使用线程池异步初始化，防止阻塞主线程
        Executors.newSingleThreadExecutor().execute {
            val serverPath = deployServerEnv()

            // 传入用户配置的模式
            initRustService(serverPath, useRootMode)

            runOnUiThread {
                startActivity(Intent(this, WebViewActivity::class.java))
                finish()
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

    private fun initRustService(serverPath: String, isRoot: Boolean) {
        try {
            if (serverPath.isNotEmpty()) {
                uniffi.rust_core.setConfig("server_path", serverPath)
            }

            // --- 核心修改逻辑 ---
            if (isRoot) {
                // Root 模式：不需要 Service 对象
                uniffi.rust_core.initService(true, AndroidLogger(), null)
                Log.i("TouchHelper", "Rust Core Initialized in ROOT mode")
            } else {
                // 无障碍模式：必须传入适配器
                // 此时 MacroAccessibilityService.instance 应该不为空（在 onCreate 检查过了）
                val adapter = AccessibilityImpl()
                uniffi.rust_core.initService(false, AndroidLogger(), adapter)
                Log.i("TouchHelper", "Rust Core Initialized in ACCESSIBILITY mode")
            }
            // --------------------

        } catch (e: Exception) {
            Log.e("TouchHelper", "Failed to init Rust Service", e)
        }
    }
}