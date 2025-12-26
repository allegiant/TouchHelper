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
        val useRootMode = getSharedPreferences("config", MODE_PRIVATE).getBoolean("use_root", false)
        Log.i("TouchHelper", "Main 启动，当前配置模式: ${if (useRootMode) "Root" else "无障碍"}")

        // 2. 部署环境并启动 (不再强制检查无障碍服务)
        Executors.newSingleThreadExecutor().execute {
            deployServerEnv()
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
}