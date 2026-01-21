package org.eu.freex.tools.common.utils

import java.awt.image.BufferedImage

/**
 * 图像特征提取器
 * 保证【生成字库】和【识别匹配】使用完全一致的二值化算法
 */
object ImageFeatureExtractor {

    fun generateBinaryData(image: BufferedImage): String {
        // 1. 统计全局亮度 (用于自适应阈值)
        // 也可以考虑引入局部阈值算法(Sauvola)以增强稳定性，但必须保证两边一致
        var totalBrightness = 0L
        var pixelCount = 0
        val width = image.width
        val height = image.height
        val pixels = IntArray(width * height)

        // 一次性获取像素数组，性能更高
        image.getRGB(0, 0, width, height, pixels, 0, width)

        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xff
            val g = (pixel shr 8) and 0xff
            val b = pixel and 0xff
            // 使用标准的亮度公式
            totalBrightness += (r * 0.299 + g * 0.587 + b * 0.114).toLong()
            pixelCount++
        }

        val avgBrightness = if (pixelCount > 0) totalBrightness / pixelCount else 128
        // 判断背景是黑还是白
        val isDarkBackground = avgBrightness < 128

        // 2. 生成 01 串
        val sb = StringBuilder(width * height)
        // 注意：遍历顺序必须一致 (这里是 行优先: y -> x)
        for (y in 0 until height) {
            for (x in 0 until width) {
                // 直接从数组取值，比 getRGB 快
                val pixel = pixels[y * width + x]
                val alpha = (pixel shr 24) and 0xff
                val r = (pixel shr 16) and 0xff
                val g = (pixel shr 8) and 0xff
                val b = pixel and 0xff
                val luma = (r * 0.299 + g * 0.587 + b * 0.114).toInt()

                // 二值化判定逻辑
                val isForeground = if (alpha < 50) {
                    false // 透明算背景
                } else if (isDarkBackground) {
                    luma > avgBrightness + 20 // 黑底：亮的算字
                } else {
                    luma < avgBrightness - 20 // 白底：暗的算字
                }

                sb.append(if (isForeground) "1" else "0")
            }
        }
        return sb.toString()
    }
}