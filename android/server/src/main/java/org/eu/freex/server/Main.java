package org.eu.freex.server;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.IBinder;

import org.eu.freex.server.GeneratedConstants;

import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class Main {

    private static final byte[] SIGNAL_FRAME = new byte[] { GeneratedConstants.SIGNAL_BYTE };
    // 智能降维：固定短边 540p
    private static final int TARGET_SHORT_SIDE = 540;

    private static Method mScreenshotMethod;
    private static Object mDisplayToken;
    private static boolean mIsInitialized = false;
    private static boolean isRunning = true;

    private static int cachedRealWidth = 0;
    private static int cachedRealHeight = 0;

    public static void main(String[] args) {
        // 🔥 重要：打印到 err，防止干扰信号
        System.err.println(">>> JAVA SERVER (MMAP + AUTO SCALE) STARTED <<<");

        // 看门狗
        new Thread(() -> {
            try {
                int r = System.in.read();
                System.exit(0);
            } catch (Exception e) {
                System.exit(0);
            }
        }).start();

        MappedByteBuffer sharedMemory = null;
        try {
            // Rust 已经创建并 chmod 777 了文件，直接打开
            RandomAccessFile file = new RandomAccessFile(GeneratedConstants.SHARED_FILE_PATH, "rw");
            sharedMemory = file.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, 4 * 1024 * 1024);
        } catch (Exception e) {
            System.err.println("Fatal: Failed to open shared memory: " + e.getMessage());
            return;
        }

        while (isRunning) {
            long start = System.currentTimeMillis();

            // 1. 获取真实分辨率
            if (cachedRealWidth == 0) {
                Bitmap temp = captureScreen(-1, -1);
                if (temp != null) {
                    cachedRealWidth = temp.getWidth();
                    cachedRealHeight = temp.getHeight();
                    temp.recycle();
                } else {
                    try { Thread.sleep(1000); } catch (Exception e) {}
                    continue;
                }
            }

            // 2. 计算缩放后的宽高和比例
            int targetW, targetH;
            float scale;

            if (cachedRealWidth < cachedRealHeight) { // 竖屏
                scale = (float)cachedRealWidth / TARGET_SHORT_SIDE; // 例如 2.0
                targetW = TARGET_SHORT_SIDE;
                targetH = (int)(cachedRealHeight / scale);
            } else { // 横屏
                scale = (float)cachedRealHeight / TARGET_SHORT_SIDE;
                targetH = TARGET_SHORT_SIDE;
                targetW = (int)(cachedRealWidth / scale);
            }

            // 3. 截取缩放图
            Bitmap hardwareBitmap = captureScreen(targetW, targetH);
            if (hardwareBitmap == null) {
                try { Thread.sleep(100); } catch (Exception e) {}
                continue;
            }

            Bitmap softwareBitmap = null;
            try {
                if (hardwareBitmap.getConfig() == Bitmap.Config.HARDWARE) {
                    softwareBitmap = hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false);
                    hardwareBitmap.recycle();
                } else {
                    softwareBitmap = hardwareBitmap;
                }

                // 4. 写入共享内存
                sharedMemory.position(0);
                sharedMemory.putInt(softwareBitmap.getWidth());  // 0-3: 宽
                sharedMemory.putInt(softwareBitmap.getHeight()); // 4-7: 高
                sharedMemory.putFloat(scale);                    // 8-11: 缩放比
                softwareBitmap.copyPixelsToBuffer(sharedMemory); // 12+: 数据

                // 5. 发送信号
                System.out.write(SIGNAL_FRAME);
                System.out.flush();

            } catch (Exception e) {
                e.printStackTrace();
                break;
            } finally {
                if (softwareBitmap != null) softwareBitmap.recycle();
            }

            long end = System.currentTimeMillis();
            long cost = end - start;
            if (cost < 30) {
                try { Thread.sleep(30 - cost); } catch (Exception e) {}
            }
        }
    }

    private static Bitmap captureScreen(int w, int h) {
        try {
            Class<?> surfaceControlClass = Class.forName("android.view.SurfaceControl");
            if (!mIsInitialized) {
                try {
                    Method method = surfaceControlClass.getMethod("getInternalDisplayToken");
                    mDisplayToken = method.invoke(null);
                } catch (Exception e) {
                    try {
                        Method method = surfaceControlClass.getMethod("getBuiltInDisplay", int.class);
                        mDisplayToken = method.invoke(null, 0);
                    } catch (Exception ignored) { mDisplayToken = null; }
                }
                try {
                    mScreenshotMethod = surfaceControlClass.getMethod("screenshot", Rect.class, int.class, int.class, int.class);
                } catch (Exception e) {
                    try {
                        mScreenshotMethod = surfaceControlClass.getMethod("screenshot", int.class, int.class);
                    } catch (Exception e2) {
                        try {
                            mScreenshotMethod = surfaceControlClass.getMethod("screenshot", IBinder.class, int.class, int.class);
                        } catch (Exception ignored) {}
                    }
                }
                mIsInitialized = true;
            }

            if (mScreenshotMethod == null) return null;

            int reqW = (w == -1) ? 0 : w;
            int reqH = (h == -1) ? 0 : h;
            int paramCount = mScreenshotMethod.getParameterTypes().length;

            if (paramCount == 4) {
                return (Bitmap) mScreenshotMethod.invoke(null, new Rect(), reqW, reqH, 0);
            } else if (paramCount == 2) {
                if (reqW == 0) { reqW = 720; reqH = 1280; }
                return (Bitmap) mScreenshotMethod.invoke(null, reqW, reqH);
            } else if (paramCount == 3) {
                return (Bitmap) mScreenshotMethod.invoke(null, mDisplayToken, reqW, reqH);
            }
        } catch (Exception e) {}
        return null;
    }
}