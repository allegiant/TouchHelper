package org.eu.freex.app

import android.util.Log
import uniffi.rust_core.AccessibilityService

class AccessibilityImpl: AccessibilityService{
    override fun dispatchClick(x: Int, y: Int) {
        val service = MacroAccessibilityService.instance
        if (service != null) {
            // Rust 传过来的是 Int 坐标，转换成 Float 传给 Service
            service.performClick(x.toFloat(), y.toFloat())
        } else {
            Log.e("AccessibilityImpl", "❌ 无法执行点击：无障碍服务未连接！")
        }
    }

    // 如果未来 Rust 定义了 swipe，也在这里实现
    // override fun dispatchSwipe(...) { ... }
}