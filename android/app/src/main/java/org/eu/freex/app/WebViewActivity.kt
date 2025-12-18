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
import androidx.core.app.ComponentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

        // 1. 优先检查是否有 Intent 传来的开发地址 (通过 adb 启动时)
        val devUrl = intent.getStringExtra("path")
        if (devUrl != null) {
            webView.loadUrl(devUrl)
        } else {
            // 2. 默认加载打包好的 assets 资源
            // 这里假设你 npm run build 后的 dist 放在了 assets/dist 目录
            webView.loadUrl("file:///android_asset/dist/index.html")
        }
    }

    override fun onResume() {
        super.onResume()
        // 注册广播接收器
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
            // 允许跨域 (开发方便)
            allowUniversalAccessFromFileURLs = true
        }

        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = WebViewClient()

        // 注入 JS 对象 "window.TouchHelper"
        webView.addJavascriptInterface(JSBridge(), "TouchHelper")
    }

    // 🔥 JS 交互接口
    inner class JSBridge {
        @JavascriptInterface
        fun runConfig(json: String) {
            Log.d("TouchHelper", "Receive Config from JS: $json")

            // 在后台线程运行 Rust 宏，防止阻塞 UI
            CoroutineScope(Dispatchers.IO).launch {
                val result = NativeLib.runMacro(json)
                Log.i("TouchHelper", "Macro Result: $result")

                // TODO: 如果需要，可以把 result 回调给 WebView
                // runOnUiThread { webView.evaluateJavascript("...", null) }
            }
        }

        @JavascriptInterface
        fun log(msg: String) {
            Log.i("TouchHelper-JS", msg)
        }
    }
}