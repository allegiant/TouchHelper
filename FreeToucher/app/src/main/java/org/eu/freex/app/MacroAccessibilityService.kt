package org.eu.freex.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class MacroAccessibilityService : AccessibilityService() {

    companion object {
        // 全局静态引用，供 NativeLib 调用
        var instance: MacroAccessibilityService? = null
    }

    // --- 核心修复点 1 ---
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("MacroService", "✅ 无障碍服务已连接 (Service Connected)!")
    }

    // --- 核心修复点 2: 防止内存泄漏 ---
    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        Log.d("MacroService", "❌ 无障碍服务已断开 (Service Unbound)!")
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun performClick(x: Float, y: Float) {
        Log.d("MacroService", "⚡ 执行点击: ($x, $y)")
        val path = Path()
        path.moveTo(x, y)
        val builder = GestureDescription.Builder()
        val gesture = builder
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        dispatchGesture(gesture, null, null)
    }
}
