package org.eu.freex.app

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.touch_core.runJsScript
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

// 引入 UniFFI 生成的全局函数
// 如果生成的代码就在 org.eu.freex.app 包下，这两行通常不需要手动写
// import org.eu.freex.app.runJsScript
// import org.eu.freex.app.setConfig

class WebViewActivity : ComponentActivity() {

    private lateinit var webView: WebView
    // 用于控制 UI 状态
    private var isScriptRunning = mutableStateOf(false)
    private var isScriptPaused = mutableStateOf(false)

    private val SCRIPT_FILENAME = "current_script.js"

    // 动态接收器，用于处理 Activity 运行时的热重载
    private val devReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "org.eu.freex.LOAD_UI") {
                val url = intent.getStringExtra("path")
                if (!url.isNullOrEmpty()) {
                    Log.i("TouchHelper", "🔥 Hot Reload: $url")
                    webView.loadUrl(url)
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 初始化 WebView 实例
        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowUniversalAccessFromFileURLs = true
            }
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
            addJavascriptInterface(JSBridge(), "TouchHelper")
        }

        // 2. 加载初始 URL
        val devUrl = intent.getStringExtra("path")
        if (devUrl != null) {
            webView.loadUrl(devUrl)
        } else {
            webView.loadUrl("file:///android_asset/dist/index.html")
        }

        // 3. 使用 Compose 布局
        setContent {
            MaterialTheme {
                Scaffold(
                    // 顶部栏：原有设置入口
                    topBar = {
                        IconButton(
                            onClick = {
                                startActivity(Intent(this@WebViewActivity, SettingsActivity::class.java))
                            },
                            modifier = Modifier
                                .padding(top = 48.dp, end = 16.dp) // 避开状态栏和圆角
                                .background(Color.White.copy(alpha = 0.7f), CircleShape) // 半透明白色背景
                                .size(40.dp)
                        ) {
                            // 使用内置图标，或者你可以用 Text("⚙️")
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color.Black
                            )
                        }
                    },
                    // 🔥 核心：原生底部控制栏
                    bottomBar = {
                        ScriptControlBar(
                            isRunning = isScriptRunning.value,
                            isPaused = isScriptPaused.value,
                            onRun = { runScript() },
                            onStop = { stopScript() },
                            onPause = { pauseScript(it) },
                            onSettings = {
                                startActivity(Intent(this@WebViewActivity, SettingsActivity::class.java))
                            }
                        )
                    }
                ) { innerPadding ->
                    // 主体内容：WebView
                    Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    /**
     * 🔥 核心逻辑：直接从 Assets 读取 script.js 并运行
     */
    private fun runScript() {
        // 1. 环境检查 (CheckEnvironment)
        val prefs = getSharedPreferences("app_config", MODE_PRIVATE)
        val useRoot = prefs.getBoolean("use_root", false)

        if (useRoot) {
            initRust(true) // 确保 Rust 服务已连接
        } else {
            if (MacroAccessibilityService.instance == null) {
                Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                return
            }
            initRust(false)
        }

        // 2. 读取脚本内容
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 直接读取 public/script.js (在 Android 中对应 assets/script.js)
                // 注意：Vite 打包后 script.js 会在 assets 根目录下，或者是 dist/script.js
                // 如果是 dev 模式，这里需要通过 HTTP 请求 localhost:5173/script.js 获取
                // 为了生产环境稳定，我们假设 assets 里有文件
                val file = File(filesDir, SCRIPT_FILENAME)
                val scriptContent = if (file.exists()) {
                    file.readText()
                } else {
                    // 2. 兜底：如果没保存过，读取 Assets 里的默认模板
                    readAssetFile("script.js")
                }

                if (scriptContent.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@WebViewActivity, "未找到脚本文件", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // 3. 调用 Rust 执行
                uniffi.touch_core.runJsScript(scriptContent)

                // 更新 UI 状态
                isScriptRunning.value = true
                isScriptPaused.value = false

            } catch (e: Exception) {
                Log.e("TouchHelper", "Run failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@WebViewActivity, "启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    private fun initRust(isRoot: Boolean) {
        try {
            if (isRoot) {
                uniffi.touch_core.initService(true, AndroidLogger(), null)
            } else {
                val adapter = AccessibilityImpl()
                uniffi.touch_core.initService(false, AndroidLogger(), adapter)
            }
        } catch (e: Exception) {
            Log.e("TouchHelper", "Init Rust failed", e)
        }
    }
    private fun stopScript() {
        CoroutineScope(Dispatchers.IO).launch {
            uniffi.touch_core.stopScript()
            isScriptRunning.value = false
            isScriptPaused.value = false
        }
    }

    private fun pauseScript(paused: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            uniffi.touch_core.setPaused(paused)
            isScriptPaused.value = paused
        }
    }

    private fun readAssetFile(fileName: String): String {
        return try {
            // 注意：Vite 打包通常会把 public 下的文件放在 assets 根目录
            // 但如果用了 subfolder，路径需要调整
            assets.open(fileName).use { inputStream ->
                InputStreamReader(inputStream).use { reader ->
                    BufferedReader(reader).readText()
                }
            }
        } catch (e: Exception) {
            Log.e("TouchHelper", "Read asset failed", e)
            ""
        }
    }




    override fun onResume() {
        super.onResume()
        val filter = IntentFilter("org.eu.freex.LOAD_UI")
        registerReceiver(devReceiver, filter, RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(devReceiver)
    }

    // 🔥 核心修改：JS 交互接口适配 UniFFI
    inner class JSBridge {
        @JavascriptInterface
        fun saveScript(script: String) {
            Log.d("TouchHelper", "Saving script, length=${script.length}")
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    openFileOutput(SCRIPT_FILENAME, MODE_PRIVATE).use {
                        it.write(script.toByteArray())
                    }
                    Log.i("TouchHelper", "Script saved to $SCRIPT_FILENAME,${script.length} bytes")
                } catch (e: Exception) {
                    Log.e("TouchHelper", "Save failed", e)
                }
            }
        }

        @JavascriptInterface
        fun log(msg: String) {
            Log.i("TouchHelper-Web", msg)
        }
    }
}

/**
 * Compose 组件：底部控制栏
 */
@Composable
fun ScriptControlBar(
    isRunning: Boolean,
    isPaused: Boolean,
    onRun: () -> Unit,
    onStop: () -> Unit,
    onPause: (Boolean) -> Unit,
    onSettings: () -> Unit
) {
    BottomAppBar(
        actions = {
            // 设置按钮
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
            // 暂停/继续 (仅运行时显示)
            if (isRunning) {
                Button(
                    onClick = { onPause(!isPaused) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPaused) Color(0xFFFAAD14) else Color(0xFF52C41A)
                    )
                ) {
                    Text(if (isPaused) "继续" else "暂停")
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { if (isRunning) onStop() else onRun() },
                containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ) {
                // 这里可以使用 Stop 和 Play 的图标
                if (isRunning) {
                    // Icon(Icons.Default.Stop, ...)
                    Text("停止", modifier = Modifier.padding(horizontal = 8.dp))
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Run")
                }
            }
        }
    )
}