use anyhow::Result;
use core::time;
use std::{
    fs::OpenOptions,
    io::{BufRead, BufReader, Read},
    process::{Command, Stdio},
    sync::Mutex,
    thread,
    time::{Duration, Instant},
};

use image::{DynamicImage, ImageBuffer, Rgba};
use lazy_static::lazy_static;
use log::{error, info};
use memmap2::MmapOptions;

use crate::domain::system::constants::{SERVER_CLASS_NAME, SHARED_FILE_PATH, SHARED_MEMORY_SIZE};
//  全局缓冲区
// 结构: (数据, 宽, 高, 行跨度, 缩放比例)
// 对应 JNI 里的 guard.0, guard.1 ...
lazy_static! {
    pub static ref SCREEN_BUFFER: Mutex<(Vec<u8>, usize, usize, usize, f32)> =
        Mutex::new((vec![], 0, 0, 0, 1.0));
}

// 封装找色逻辑辅助函数
pub fn find_color_helper(
    target_rgb: (u8, u8, u8),
    tolerance: u8,
    region: Option<Vec<i32>>,
) -> Option<(i32, i32)> {
    let guard = SCREEN_BUFFER.lock().unwrap();
    let pixels = &guard.0;
    let w = guard.1;
    let h = guard.2;
    let stride = guard.3;
    let scale = guard.4;

    if pixels.is_empty() {
        return None;
    }

    let rect = region
        .map(|r| {
            (
                (r[0] as f32 / scale) as usize,
                (r[1] as f32 / scale) as usize,
                (r[2] as f32 / scale) as usize,
                (r[3] as f32 / scale) as usize,
            )
        })
        .unwrap_or((0, 0, w, h));

    // 调用底层的 find_color_in_buffer
    find_color_in_buffer(pixels, w, h, stride, target_rgb, tolerance, rect)
}

pub fn parse_hex_color(hex: &str) -> (u8, u8, u8) {
    let hex = hex.trim_start_matches('#');
    if hex.len() != 6 {
        return (0, 0, 0);
    }
    let r = u8::from_str_radix(&hex[0..2], 16).unwrap_or(0);
    let g = u8::from_str_radix(&hex[2..4], 16).unwrap_or(0);
    let b = u8::from_str_radix(&hex[4..6], 16).unwrap_or(0);
    (r, g, b)
}

pub fn is_color_match(r1: u8, g1: u8, b1: u8, r2: u8, g2: u8, b2: u8, tolerance: u8) -> bool {
    let dr = (r1 as i32) - (r2 as i32);
    let dg = (g1 as i32) - (g2 as i32);
    let db = (b1 as i32) - (b2 as i32);
    let distance_sq = dr * dr + dg * dg + db * db;
    let tolerance_sq = (tolerance as i32) * (tolerance as i32);
    distance_sq <= tolerance_sq
}

pub fn find_color_in_buffer(
    pixels: &[u8],
    _buffer_width: usize,
    buffer_height: usize,
    stride: usize,
    target_rgb: (u8, u8, u8),
    tolerance: u8,
    search_rect: (usize, usize, usize, usize),
) -> Option<(i32, i32)> {
    let (tr, tg, tb) = target_rgb;
    let (sx, sy, w, h) = search_rect;
    let end_y = (sy + h).min(buffer_height);
    let end_x = sx + w;

    for y in sy..end_y {
        for x in sx..end_x {
            let offset = y * stride + x * 4;
            if offset + 3 >= pixels.len() {
                continue;
            }

            // BGR -> RGB 转换
            let b = pixels[offset];
            let g = pixels[offset + 1];
            let r = pixels[offset + 2];

            if is_color_match(r, g, b, tr, tg, tb, tolerance) {
                return Some((x as i32, y as i32));
            }
        }
    }
    None
}

// 只负责算坐标，不负责点
pub fn map_coordinates(x: i32, y: i32) -> (i32, i32) {
    let scale = {
        let guard = SCREEN_BUFFER.lock().unwrap();
        guard.4
    };
    let real_x = (x as f32 * scale) as i32;
    let real_y = (y as f32 * scale) as i32;
    (real_x, real_y)
}

// --- 5. 核心逻辑：Root Server 启动 (保持原样) ---

pub fn start_root_server_internal(jar_path: String) {
    info!("Rust: 正在清理旧的 Java 进程...");
    let _ = Command::new("su")
        .arg("-c")
        .arg(format!("pkill -f {}", SERVER_CLASS_NAME))
        .output();
    thread::sleep(time::Duration::from_millis(200));

    info!("Rust: 委托 Root 创建共享内存文件...");
    let setup_cmd = format!(
        "touch {} && chmod 777 {} && truncate -s {} {}",
        SHARED_FILE_PATH, SHARED_FILE_PATH, SHARED_MEMORY_SIZE, SHARED_FILE_PATH
    );

    let setup_res = Command::new("su").arg("-c").arg(&setup_cmd).output();
    match setup_res {
        Ok(o) if o.status.success() => info!("Rust: 文件创建/权限设置成功"),
        _ => error!("Rust: ⚠️ 文件创建可能失败，后续 mmap 可能会出错"),
    }

    info!("Rust: 启动 Java Server, Jar: {}", jar_path);
    let cmd = format!(
        "CLASSPATH={} /system/bin/app_process /system/bin {}",
        jar_path, SERVER_CLASS_NAME
    );

    let child = Command::new("su")
        .arg("-c")
        .arg(cmd)
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn();

    let mut child = match child {
        Ok(c) => c,
        Err(e) => {
            error!("Rust: ❌ 无法启动 Root Server: {:?}", e);
            return;
        }
    };

    let stdout = child.stdout.take().expect("Failed stdout");
    let stderr = child.stderr.take().expect("Failed stderr");

    thread::spawn(move || {
        let reader = BufReader::new(stderr);
        for line in reader.lines() {
            if let Ok(l) = line {
                error!("🔴 Java Stderr: {}", l);
            }
        }
    });

    thread::spawn(move || {
        let _keep_alive = child;
        thread::sleep(time::Duration::from_millis(500));

        let file = match OpenOptions::new()
            .read(true)
            .write(true)
            .open(SHARED_FILE_PATH)
        {
            Ok(f) => f,
            Err(e) => {
                error!(
                    "Rust: ❌ 无法打开共享内存文件 (可能 Root 初始化失败): {:?}",
                    e
                );
                return;
            }
        };

        let mmap = match unsafe { MmapOptions::new().map(&file) } {
            Ok(m) => m,
            Err(e) => {
                error!("Rust: ❌ mmap 失败: {:?}", e);
                return;
            }
        };

        info!(
            "Rust: ✅ MMAP 映射成功 (Size: {})，开始监听信号...",
            mmap.len()
        );

        let mut reader = BufReader::new(stdout);
        let mut signal = [0u8; 1];

        loop {
            match reader.read_exact(&mut signal) {
                Ok(_) => {
                    if signal[0] == 0xAA {
                        if mmap.len() < 12 {
                            continue;
                        }
                        let w_bytes: [u8; 4] = mmap[0..4].try_into().unwrap();
                        let h_bytes: [u8; 4] = mmap[4..8].try_into().unwrap();
                        let width = u32::from_be_bytes(w_bytes) as usize;
                        let height = u32::from_be_bytes(h_bytes) as usize;

                        let s_bytes: [u8; 4] = mmap[8..12].try_into().unwrap();
                        let scale = f32::from_be_bytes(s_bytes);

                        let frame_size = width * height * 4;
                        if mmap.len() < 12 + frame_size {
                            continue;
                        }

                        let pixels = &mmap[12..12 + frame_size];

                        if let Ok(mut guard) = SCREEN_BUFFER.lock() {
                            if guard.0.len() != frame_size {
                                guard.0.resize(frame_size, 0);
                            }
                            guard.0.copy_from_slice(pixels);
                            guard.1 = width;
                            guard.2 = height;
                            guard.3 = width * 4;
                            guard.4 = scale;
                        }
                    }
                }
                Err(e) => {
                    error!("Rust: Java Server 管道断开: {:?}", e);
                    break;
                }
            }
        }
    });
}

// ==========================================
// 屏幕截图核心函数
// ==========================================
pub fn capture_screen() -> Result<DynamicImage> {
    let guard = SCREEN_BUFFER.lock().unwrap();
    let pixels = &guard.0;
    let width = guard.1 as u32;
    let height = guard.2 as u32;
    let scale = guard.4; // 获取缩放比例 (例如 2.0 表示 Buffer 是屏幕的 1/2)

    if pixels.is_empty() || width == 0 || height == 0 {
        return Err(anyhow::anyhow!(
            "屏幕服务尚未就绪或画面为空 (Screen Buffer Empty)"
        ));
    }

    // 1. 构建原始 ImageBuffer (BGRA -> RGBA)
    let mut rgba_pixels = Vec::with_capacity(pixels.len());
    let stride = guard.3;

    for y in 0..height {
        let row_start = (y as usize) * stride;
        let row_end = row_start + (width as usize) * 4;
        if row_end > pixels.len() {
            break;
        }

        let row_pixels = &pixels[row_start..row_end];
        for chunk in row_pixels.chunks_exact(4) {
            rgba_pixels.push(chunk[2]); // R
            rgba_pixels.push(chunk[1]); // G
            rgba_pixels.push(chunk[0]); // B
            rgba_pixels.push(chunk[3]); // A
        }
    }

    let buffer = ImageBuffer::<Rgba<u8>, _>::from_raw(width, height, rgba_pixels)
        .ok_or_else(|| anyhow::anyhow!("无法构建 ImageBuffer"))?;

    let img = DynamicImage::ImageRgba8(buffer);

    // 🔥 核心修复：如果存在缩放，则还原回原始尺寸
    // 这里使用 Nearest (最近邻) 插值，速度最快且保持边缘清晰，最适合 OCR/找色
    if (scale - 1.0).abs() > 0.001 {
        // scale 通常表示 "屏幕是Buffer的多少倍" (例如 2.0)
        let new_w = (width as f32 * scale) as u32;
        let new_h = (height as f32 * scale) as u32;
        return Ok(img.resize(new_w, new_h, image::imageops::FilterType::Nearest));
    }

    Ok(img)
}

// 🔥 新增：等待服务就绪 (阻塞直到收到第一帧或超时)
pub fn wait_for_service_ready(timeout_ms: u64) -> bool {
    let start = Instant::now();
    let timeout = Duration::from_millis(timeout_ms);

    loop {
        if let Ok(guard) = SCREEN_BUFFER.lock() {
            // 只要 buffer 不为空且宽高有效，说明已就绪
            if !guard.0.is_empty() && guard.1 > 0 && guard.2 > 0 {
                return true;
            }
        }

        if start.elapsed() > timeout {
            return false;
        }

        thread::sleep(Duration::from_millis(50)); // 每 50ms 检查一次
    }
}
