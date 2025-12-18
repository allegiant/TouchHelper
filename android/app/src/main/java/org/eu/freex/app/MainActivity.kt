package org.eu.freex.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import java.io.File
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 🔥 加这一行！这会让系统强制创建外部存储目录，并授予读写权限
        //getExternalFilesDir(null)
        //setContent { MacroApp() }
        // 使用线程池异步初始化，防止阻塞主线程
        Executors.newSingleThreadExecutor().execute {
            initServerEnv()

            // 初始化完成后跳转到 WebView
            runOnUiThread {
                startActivity(Intent(this, WebViewActivity::class.java))
                finish() // 关闭当前 Activity
            }
        }
    }

    private fun initServerEnv() {
        try {
            // 1. 确定 server.jar 路径 (App 私有目录)
            val serverFile = File(filesDir, "server.jar")

            // 2. 每次启动都从 Assets 覆盖，确保是最新的代码
            // (生产环境可以加版本判断，开发环境建议每次覆盖)
            assets.open("server.jar").use { input ->
                serverFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // 赋予可读可执行权限 (以防万一)
            serverFile.setReadable(true, false)
            serverFile.setExecutable(true, false)

            Log.i("TouchHelper", "Server JAR deployed to: ${serverFile.absolutePath}")

            // 3. 获取屏幕尺寸
            val metrics = resources.displayMetrics

            // 4. 让 Rust 启动 Java Server
            // 注意：Rust 端需要修改 start_root_server_internal 接收这个 path
            NativeLib.startRootServer(serverFile.absolutePath, metrics.widthPixels, metrics.heightPixels)

        } catch (e: Exception) {
            Log.e("TouchHelper", "Failed to init server environment", e)
        }
    }
}