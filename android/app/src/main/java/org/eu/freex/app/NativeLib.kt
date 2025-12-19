package org.eu.freex.app

import android.util.Log
import uniffi.rust_core.PlatformCallback
import uniffi.rust_core.runCoreMacro
import uniffi.rust_core.startCoreRootServer
import java.nio.ByteBuffer

class AndroidPlatformCallback: PlatformCallback {
    override fun dispatchClick(x: Int, y: Int) {
        // 调用无障碍服务执行点击
        // 需确保 MacroAccessibilityService 有一个全局 instance 或静态方法
        MacroAccessibilityService.instance?.performClick(x.toFloat(),y.toFloat());
    }

    override fun log(msg: String) {
        Log.i("RustCore", msg)
    }

}

object NativeLib {
    init { System.loadLibrary("rust_core") }


    // ==============================================================
    // A. 视觉流接口 (保留的手写 JNI，用于高性能传图)
    // ==============================================================
    external fun updateScreenBuffer(
        buffer: ByteBuffer,
        w: Int,
        h: Int,
        stride: Int
    )
    external fun pushScreenImage(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        pixelStride: Int,
        rowStride: Int,
        scale: Float // 新增
    )

    // ==============================================================
    // B. 逻辑流接口 (通过 UniFFI 调用 Rust)
    // ==============================================================

    /**
     * 启动 Root Server
     */
    fun startRootServer(jarPath: String) {
        // 直接调用 UniFFI 生成的顶层函数
        try {
            startCoreRootServer(jarPath)
        } catch (e: Exception) {
            Log.e("NativeLib", "Failed to start root server", e)
        }
    }

    /**
     * 运行宏任务
     * 注意：Rust 的 runMacro 可能会阻塞线程（因为它包含 sleep），所以务必在子线程调用！
     */
    fun runMacro(jsonConfig: String) {
        Thread {
            try {
                Log.i("NativeLib", "Starting macro with config length: ${jsonConfig.length}")

                // 实例化回调
                val callback = AndroidPlatformCallback()

                // 调用 Rust 函数，传入配置和回调
                runCoreMacro(jsonConfig, callback)

            } catch (e: Exception) {
                Log.e("NativeLib", "Macro execution failed", e)
            }
        }.start()
    }
}