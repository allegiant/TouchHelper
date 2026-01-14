package org.eu.freex.tools.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Window
import java.awt.image.BufferedImage

interface ScreenCaptureService {
    suspend fun captureFullscreen(): BufferedImage
}

class DesktopScreenCaptureService : ScreenCaptureService {
    override suspend fun captureFullscreen(): BufferedImage {
        val visibleWindows = withContext(Dispatchers.Main) {
            val windows = Window.getWindows().filter { it.isVisible }
            windows.forEach { it.isVisible = false }
            windows
        }

        return try {
            // 给窗口隐藏一点时间动画
            delay(300)

            withContext(Dispatchers.IO) {
                val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
                val screens = ge.screenDevices
                var logicalBounds = Rectangle()

                // 计算所有屏幕的逻辑总大小
                for (screen in screens) {
                    logicalBounds = logicalBounds.union(screen.defaultConfiguration.bounds)
                }

                val robot = Robot()

                // 使用多分辨率截图 (适配高分屏)
                val mri = robot.createMultiResolutionScreenCapture(logicalBounds)
                val variants = mri.resolutionVariants

                // 获取最高清的物理像素图
                val bestVariant = variants.maxByOrNull { it.getWidth(null) }

                bestVariant as? BufferedImage ?: robot.createScreenCapture(logicalBounds)
            }
        } finally {
            // 2. 【UI 操作】恢复窗口显示
            withContext(Dispatchers.Main) {
                visibleWindows.forEach {
                    it.isVisible = true
                    it.toFront()
                }
            }
        }
    }
}