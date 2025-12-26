package org.eu.freex.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager

object ScreenCaptureManager {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    // 标记是否正在录屏
    var isCapturing = false

    @SuppressLint("WrongConstant")
    fun startCapture(context: Context, resultCode: Int, data: Intent) {
        // 1. 获取 MediaProjection
        val mpManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpManager.getMediaProjection(resultCode, data)

        // 2. 获取屏幕尺寸
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)

        // 使用全分辨率
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        // 3. 创建 ImageReader (关键：RGBA_8888 格式，只缓存 2 张图防止爆内存)
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        // 4. 创建虚拟显示器 (开始录屏)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null, null
        )

        // 5. 监听新图片
        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val planes = image.planes
                val plane = planes[0]
                val buffer = plane.buffer

                val width = image.width
                val height = image.height
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride

                // 将 Buffer 传给 Rust
                // 注意：Rust 端会自动处理 stride，我们直接把原始数据扔进去
                NativeLib.pushScreenImage(buffer, width, height, pixelStride, rowStride,1.0f)

            } catch (e: Exception) {
                Log.e("ScreenCap", "Error", e)
            } finally {
                image.close() // 必须关闭！否则几帧之后就不动了
            }
        }, Handler(Looper.getMainLooper()))

        isCapturing = true
        Log.d("ScreenCap", "🎥 屏幕录制已开启，数据流向 Rust...")
    }

    fun stopCapture() {
        isCapturing = false
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        Log.d("ScreenCap", "屏幕录制已停止")
    }
}