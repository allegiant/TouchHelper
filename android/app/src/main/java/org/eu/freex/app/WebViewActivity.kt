package org.eu.freex.app

import android.provider.Settings
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// 引入 UniFFI 生成的全局函数
// 如果生成的代码就在 org.eu.freex.app 包下，这两行通常不需要手动写
// import org.eu.freex.app.runJsScript
// import org.eu.freex.app.setConfig

class WebViewActivity : ComponentActivity() {

    private lateinit var webView: WebView

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
                MainScreen()
            }
        }
    }

    @Composable
    fun MainScreen() {
        Box(modifier = Modifier.fillMaxSize()) {
            // 底层：WebView
            // AndroidView 允许在 Compose 中显示传统 View
            AndroidView(
                factory = { webView }, // 直接返回已经初始化好的 webView 实例
                modifier = Modifier.fillMaxSize()
            )

            // 上层：设置按钮 (右上角)
            IconButton(
                onClick = {
                    startActivity(Intent(this@WebViewActivity, SettingsActivity::class.java))
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
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
        /**
         * 🔥 新增：检查运行环境
         * 返回 true 表示环境就绪，可以运行；false 表示已触发跳转设置或权限不足
         */
        @JavascriptInterface
        fun checkEnvironment(): Boolean {
            val prefs = getSharedPreferences("app_config", MODE_PRIVATE)
            val useRoot = prefs.getBoolean("use_root", false)
            Log.d("TouchHelper", "Check Env (Native Config): RootMode=$useRoot")

            if (useRoot) {
                // Root 模式
                initRust(true)
                return true
            } else {
                // 无障碍模式
                if (MacroAccessibilityService.instance == null) {
                    Toast.makeText(this@WebViewActivity, "请开启无障碍服务", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    return false
                }
                initRust(false)
                return true
            }
        }

        private fun initRust(isRoot: Boolean) {
            try {
                if (isRoot) {
                    uniffi.rust_core.initService(true, AndroidLogger(), null)
                } else {
                    val adapter = AccessibilityImpl()
                    uniffi.rust_core.initService(false, AndroidLogger(), adapter)
                }
            } catch (e: Exception) {
                Log.e("TouchHelper", "Init Rust failed", e)
            }
        }

        /**
         * 运行 JS 脚本
         * 前端调用: window.TouchHelper.runScript("Device.click(100, 200);")
         */
        @JavascriptInterface
        fun runScript(script: String) {
            Log.d("TouchHelper", "Running Script...")
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    uniffi.rust_core.runJsScript(script)
                } catch (e: Exception) {
                    Log.e("TouchHelper", "Run Error", e)
                }
            }
        }

        /**
         * 🔥 新增：停止脚本
         * 注意：需要在 Rust 端实现对应的 stopScript 导出函数
         */
        @JavascriptInterface
        fun stopScript() {
            Log.d("TouchHelper", "Stop Script Signal")
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    uniffi.rust_core.stopScript()
                } catch (e: Exception) {
                    Log.e("TouchHelper", "Stop Error", e)
                }
            }
        }

        /**
         * 🔥 新增：暂停脚本
         */
        @JavascriptInterface
        fun pauseScript(isPaused: Boolean) {
            Log.d("TouchHelper", "Pause Script: $isPaused")
            CoroutineScope(Dispatchers.IO).launch {
                uniffi.rust_core.setPaused(isPaused)
            }
        }

        @JavascriptInterface
        fun setConfig(key: String, value: String) {
            val prefs = getSharedPreferences("config", MODE_PRIVATE)
            prefs.edit { putString(key, value) }
            CoroutineScope(Dispatchers.IO).launch {
                uniffi.rust_core.setConfig(key, value)
            }
        }

        @JavascriptInterface
        fun log(msg: String) {
            Log.i("TouchHelper-Web", msg)
        }
    }
}