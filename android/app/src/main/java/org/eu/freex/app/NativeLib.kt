package org.eu.freex.app

import java.nio.ByteBuffer

object NativeLib {
    init { System.loadLibrary("rust_core") }

    external fun runMacro(jsonString: String): String

    // 启动服务 (无需传宽高，协议自动处理)
    external fun startRootServer(jarPath: String, w: Int, h: Int)

    // 🔥 新增：推送录屏数据给 Rust
    external fun pushScreenImage(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        pixelStride: Int,
        rowStride: Int,
        scale: Float // 新增
    )
}