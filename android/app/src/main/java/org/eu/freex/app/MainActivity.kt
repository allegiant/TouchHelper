package org.eu.freex.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import uniffi.rust_core.PlatformLogger
import java.io.File
import java.util.concurrent.Executors

class AndroidLogger : PlatformLogger {
    override fun log(msg: String) {
        Log.i("RustCore", msg)
    }
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 获取上次保存的模式配置 (默认无障碍)
        val useRootMode = getSharedPreferences("config", MODE_PRIVATE)
            .getBoolean("use_root", false)
        Log.i("TouchHelper", "Main 启动，预设模式: ${if (useRootMode) "Root" else "无障碍"}")

        // 2. 部署环境并启动 (不再强制检查无障碍服务)
        Executors.newSingleThreadExecutor().execute {
            val serverPath = deployServerEnv()

            // 尝试初始化 Rust (尽力而为)
            // 如果是无障碍模式但服务没开，initRustService 内部会处理（传入 null adapter 或暂不初始化）
            initRustService(serverPath, useRootMode)

            runOnUiThread {
                startActivity(Intent(this, WebViewActivity::class.java))
                // 不再 finish()，保持 MainActivity 在栈底也可以，或者 finish 均可
                // 这里为了逻辑简单，还是 finish 掉，全权交给 WebViewActivity 接管
                finish()
            }
        }
    }

    private fun deployServerEnv(): String {
        try {
            val serverFile = File(filesDir, "server.jar")
            if (serverFile.exists()) serverFile.delete() // 每次覆盖
            assets.open("server.jar").use { input ->
                serverFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            serverFile.setReadable(true, false)
            serverFile.setExecutable(true, false)
            return serverFile.absolutePath
        } catch (e: Exception) {
            Log.e("TouchHelper", "Server deploy failed", e)
            return ""
        }
    }

    private fun initRustService(serverPath: String, isRoot: Boolean) {
        try {
            if (serverPath.isNotEmpty()) {
                uniffi.rust_core.setConfig("server_path", serverPath)
            }

            if (isRoot) {
                uniffi.rust_core.initService(true, AndroidLogger(), null)
            } else {
                // 尝试获取无障碍实例
                val service = MacroAccessibilityService.instance
                if (service != null) {
                    val adapter = AccessibilityImpl()
                    uniffi.rust_core.initService(false, AndroidLogger(), adapter)
                } else {
                    Log.w("TouchHelper", "无障碍服务尚未开启，Rust Service 暂未完全初始化 (等待用户手动开启)")
                }
            }
        } catch (e: Exception) {
            Log.e("TouchHelper", "Init Rust Service failed", e)
        }
    }
}