package org.eu.freex.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 接收来自电脑端 (adb) 的指令
 * 命令: adb shell am broadcast -a org.eu.freex.LOAD_UI --es path "http://..."
 */
class DevReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "org.eu.freex.LOAD_UI") {
            val url = intent.getStringExtra("path")
            Log.i("TouchHelper", "DevReceiver got url: $url")

            // 启动 WebViewActivity 并传入 URL
            val activityIntent = Intent(context, WebViewActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("path", url)
            }
            context.startActivity(activityIntent)
        }
    }
}