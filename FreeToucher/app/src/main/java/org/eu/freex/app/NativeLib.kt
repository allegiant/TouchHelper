package org.eu.freex.app

import java.nio.ByteBuffer

object NativeLib {
    init {
        // 加载 Rust 编译生成的库
        System.loadLibrary("touch__core")
    }


    // ==============================================================
    // A. 视觉流接口 (保留的手写 JNI，用于高性能传图)
    // ==============================================================
    external fun updateScreenBuffer(
        buffer: ByteBuffer,
        w: Int,
        h: Int,
        stride: Int
    )

    // 把截图数据推给 Rust (零拷贝)
    external fun pushScreenImage(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        pixelStride: Int,
        rowStride: Int,
        scale: Float
    )
}