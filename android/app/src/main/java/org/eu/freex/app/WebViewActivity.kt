package org.eu.freex.app

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.core.content.edit

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
        webView = WebView(this)
        setContentView(webView)

        setupWebView()

        // 1. 优先检查是否有 Intent 传来的开发地址
        val devUrl = intent.getStringExtra("path")
        if (devUrl != null) {
            webView.loadUrl(devUrl)
        } else {
            // 2. 默认加载打包好的 assets 资源
            webView.loadUrl("file:///android_asset/dist/index.html")
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter("org.eu.freex.LOAD_UI")
        registerReceiver(devReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(devReceiver)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowUniversalAccessFromFileURLs = true
        }

        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = WebViewClient()

        // 注入 JS 对象 "window.TouchHelper"
        webView.addJavascriptInterface(JSBridge(), "TouchHelper")
    }

    // 🔥 核心修改：JS 交互接口适配 UniFFI
    inner class JSBridge {

        /**
         * 运行 JS 脚本 (替代原来的 runMacro)
         * 前端调用: window.TouchHelper.runScript("Device.click(100, 200);")
         */
        @JavascriptInterface
        fun runScript(script: String) {
            Log.d("TouchHelper", "Running JS Script...")

            // 虽然 runJsScript 在 Rust 内部是新开线程，但为了防止 JNI 调用本身卡顿 UI，
            // 建议放在 IO 线程调用
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // 直接调用 UniFFI 生成的函数
                    uniffi.rust_core.runJsScript(script)
                } catch (e: Exception) {
                    Log.e("TouchHelper", "Script Error", e)
                }
            }
        }

        /**
         * 保存配置
         * 前端调用: window.TouchHelper.setConfig("game_mode", "1")
         */
        @JavascriptInterface
        fun setConfig(key: String, value: String) {
            Log.d("TouchHelper", "Set Config: $key = $value")

            // ✅ 1. 同步保存到 Android SharedPreferences
            // 只有保存了，下次 MainActivity 启动时才能读到正确的值
            val prefs = getSharedPreferences("config", MODE_PRIVATE)
            prefs.edit {
                if (key == "use_root") {
                    // 特殊处理 bool 类型
                    putBoolean(key, value == "true")
                } else {
                    // 其他配置存字符串
                    putString(key, value)
                }
            } // 异步提交

            // ✅ 2. 通知 Rust (内存更新)
            CoroutineScope(Dispatchers.IO).launch {
                uniffi.rust_core.setConfig(key, value)
            }

            // 可选：如果是切换 Root 模式，通常需要提示用户重启 App 才能生效
            if (key == "use_root") {
                Toast.makeText(this@WebViewActivity, "配置已保存，请重启应用生效", Toast.LENGTH_LONG).show()
            }
        }

        /**
         * 日志打印
         */
        @JavascriptInterface
        fun log(msg: String) {
            Log.i("TouchHelper-Web", msg)
        }
    }
}