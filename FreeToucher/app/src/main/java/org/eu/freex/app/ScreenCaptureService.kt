package org.eu.freex.app

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ScreenCaptureService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1. 从 Intent 获取 Activity 传过来的录屏结果代码和数据
        val resultCode = intent?.getIntExtra("RESULT_CODE", 0) ?: 0
        val resultData = intent?.getParcelableExtra("RESULT_DATA") as? Intent

        if (resultCode == 0 || resultData == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        // 2. 创建通知渠道 (Android 8.0+)
        createNotificationChannel()

        // 3. 构建通知
        val notification = NotificationCompat.Builder(this, "screen_capture_channel")
            .setContentTitle("简易宏")
            .setContentText("正在后台运行屏幕识别...")
            .setSmallIcon(R.drawable.ic_menu_camera) // 这里随便找个系统图标，或者用你自己的 R.drawable.ic_xxx
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        // 4. 启动前台服务 (这是解决崩溃的关键！)
        // Android 14 必须指定 FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(1, notification)
        }

        // 5. 服务启动好了，现在可以安全地开始录屏了
        ScreenCaptureManager.startCapture(this, resultCode, resultData)

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        ScreenCaptureManager.stopCapture()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "screen_capture_channel",
                "Screen Capture Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}