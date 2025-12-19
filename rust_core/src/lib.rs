use android_logger::Config;
use jni::objects::{JByteBuffer, JClass, JString, JValue};
use jni::sys::jstring;
use jni::JNIEnv;
use lazy_static::lazy_static;
use log::{error, info};
use memmap2::MmapOptions;
use serde::{Deserialize, Serialize};
use std::fs::OpenOptions;
use std::io::{BufRead, BufReader, Read};
use std::process::{Command, Stdio};
use std::sync::Mutex;
use std::{thread, time};
use ts_rs::TS;

use crate::constants::{NATVIE_LIB_PATH, SERVER_CLASS_NAME, SHARED_FILE_PATH};

mod constants;

#[cfg(test)]
mod export;

// 🔥 启用 UniFFI
uniffi::setup_scaffolding!();

// --- 1. 全局变量 ---
lazy_static! {
    static ref SCREEN_BUFFER: Mutex<(Vec<u8>, usize, usize, usize, f32)> =
        Mutex::new((vec![], 0, 0, 0, 1.0));
}

// --- 2. UniFFI 回调接口 ---
// 让 Kotlin 实现这个接口，用于接收日志和点击指令
#[uniffi::export(callback_interface)]
pub trait PlatformCallback: Send + Sync {
    fn dispatch_click(&self, x: i32, y: i32);
    fn log(&self, msg: String);
}

// --- 3. 数据结构定义 ---
#[derive(Serialize, Deserialize, Debug, TS)]
#[ts(export)]
#[serde(tag = "type")]
enum Action {
    Click {
        x: i32,
        y: i32,
        delay_ms: u64,
    },
    Wait {
        ms: u64,
    },
    Log {
        msg: String,
    },
    FindAndClick {
        color_html: String,
        tolerance: u8,
        region: Option<Vec<i32>>,
    },
}

#[derive(Serialize, Deserialize, Debug, TS)]
#[ts(export)]
struct MacroConfig {
    loop_count: i32,
    use_root: bool,
    actions: Vec<Action>,
}

// --- 4. 辅助函数 ---

fn init_logger() {
    let _ = android_logger::init_once(
        Config::default()
            .with_max_level(log::LevelFilter::Info)
            .with_tag("RustLogic"),
    );
}

fn parse_hex_color(hex: &str) -> (u8, u8, u8) {
    let hex = hex.trim_start_matches('#');
    if hex.len() != 6 {
        return (0, 0, 0);
    }
    let r = u8::from_str_radix(&hex[0..2], 16).unwrap_or(0);
    let g = u8::from_str_radix(&hex[2..4], 16).unwrap_or(0);
    let b = u8::from_str_radix(&hex[4..6], 16).unwrap_or(0);
    (r, g, b)
}

fn is_color_match(r1: u8, g1: u8, b1: u8, r2: u8, g2: u8, b2: u8, tolerance: u8) -> bool {
    let dr = (r1 as i32) - (r2 as i32);
    let dg = (g1 as i32) - (g2 as i32);
    let db = (b1 as i32) - (b2 as i32);
    let distance_sq = dr * dr + dg * dg + db * db;
    let tolerance_sq = (tolerance as i32) * (tolerance as i32);
    distance_sq <= tolerance_sq
}

fn find_color_in_buffer(
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

// 简单的 Root 点击命令
fn perform_root_click_cmd(x: i32, y: i32) {
    let cmd = format!("input tap {} {}", x, y);
    let _ = Command::new("su").arg("-c").arg(cmd).output();
}

// 🔥 修复：参数改为接收 callback，不再需要 JNIEnv
fn perform_click(callback: &Box<dyn PlatformCallback>, use_root: bool, x: i32, y: i32) {
    let scale = {
        let guard = SCREEN_BUFFER.lock().unwrap();
        guard.4
    };
    let real_x = (x as f32 * scale) as i32;
    let real_y = (y as f32 * scale) as i32;

    if use_root {
        perform_root_click_cmd(real_x, real_y);
    } else {
        // 🔥 修复：使用 UniFFI 回调，而不是 JNI 调用
        callback.dispatch_click(real_x, real_y);
    }
}

// --- 5. 核心逻辑：Root Server 启动 (保持原样) ---

fn start_root_server_internal(jar_path: String) {
    info!("Rust: 正在清理旧的 Java 进程...");
    let _ = Command::new("su")
        .arg("-c")
        .arg(format!("pkill -f {}", SERVER_CLASS_NAME))
        .output();
    thread::sleep(time::Duration::from_millis(200));

    // 使用 4MB 默认大小
    let buffer_size = 4 * 1024 * 1024;

    info!("Rust: 委托 Root 创建共享内存文件...");
    let setup_cmd = format!(
        "touch {} && chmod 777 {} && truncate -s {} {}",
        SHARED_FILE_PATH, SHARED_FILE_PATH, buffer_size, SHARED_FILE_PATH
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

    let mut child = Command::new("su")
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

// --- 6. UniFFI 导出函数 (替代原有的 JNI runMacro 等) ---

#[uniffi::export]
pub fn start_core_root_server(jar_path: String) {
    init_logger();
    start_root_server_internal(jar_path);
}

#[uniffi::export]
pub fn run_core_macro(config_json: String, callback: Box<dyn PlatformCallback>) {
    init_logger();

    // 错误处理：保留原有的格式返回给 Log 回调
    let config: MacroConfig = match serde_json::from_str(&config_json) {
        Ok(c) => c,
        Err(e) => {
            let msg = format!("Error: {}", e);
            callback.log(msg); // 模拟原来的 return env.new_string
            return;
        }
    };

    let use_root = config.use_root;

    for _ in 0..config.loop_count {
        for action in &config.actions {
            match action {
                Action::Click { x, y, delay_ms } => {
                    perform_click(&callback, use_root, *x, *y);
                    thread::sleep(time::Duration::from_millis(*delay_ms));
                }
                Action::Wait { ms } => {
                    thread::sleep(time::Duration::from_millis(*ms));
                }
                Action::Log { msg } => {
                    info!("Macro Log: {}", msg);
                    // 保持原有逻辑：记录日志
                    // 原代码是 log_acc.push_str(...)，这里直接发给前端
                    callback.log(format!("{}\n", msg));
                }
                Action::FindAndClick {
                    color_html,
                    tolerance,
                    region,
                } => {
                    let target_rgb = parse_hex_color(color_html);
                    let mut found_pos = None;
                    let mut debug_color = (0, 0, 0);
                    let mut final_scale = 1.0;

                    {
                        let guard = SCREEN_BUFFER.lock().unwrap();
                        let pixels = &guard.0;
                        let w = guard.1;
                        let h = guard.2;
                        let stride = guard.3;
                        final_scale = guard.4;

                        if !pixels.is_empty() {
                            let rect = region
                                .clone()
                                .map(|r| {
                                    let sx = (r[0] as f32 / final_scale) as usize;
                                    let sy = (r[1] as f32 / final_scale) as usize;
                                    let sw = (r[2] as f32 / final_scale) as usize;
                                    let sh = (r[3] as f32 / final_scale) as usize;
                                    (sx, sy, sw, sh)
                                })
                                .unwrap_or((0, 0, w, h));

                            let offset = rect.1 * stride + rect.0 * 4;
                            if offset + 3 < pixels.len() {
                                debug_color =
                                    (pixels[offset + 2], pixels[offset + 1], pixels[offset]);
                            }

                            found_pos = find_color_in_buffer(
                                pixels, w, h, stride, target_rgb, *tolerance, rect,
                            );
                        }
                    }

                    if let Some((x, y)) = found_pos {
                        let real_x = (x as f32 * final_scale) as i32;
                        let real_y = (y as f32 * final_scale) as i32;

                        // ⚠️ 保持原有的日志内容不变
                        let log_msg = format!(
                            "Rust: ✅ 找到颜色 {} @ 小图({}, {}) -> 原图({}, {})",
                            color_html, x, y, real_x, real_y
                        );
                        info!("{}", log_msg);
                        callback.log(log_msg);
                        perform_click(&callback, use_root, x, y);
                    } else {
                        // ⚠️ 保持原有的日志内容不变
                        let log_msg = format!(
                            "Rust: ❌ 未找到 {}. 起点颜色: #{:02X}{:02X}{:02X}",
                            color_html, debug_color.0, debug_color.1, debug_color.2
                        );
                        info!("{}", log_msg);
                        // 原代码里这里只打了 info，并没有加到 log_acc，所以是否回调给前端看你需求
                        // 为了调试方便，建议也回调一下：
                        callback.log(log_msg);
                    }
                }
            }
        }
    }
    // 原 runMacro 返回的是 log_acc，现在通过 callback.log 分段发送了，这里不需要返回
    // 如果需要发送结束信号：
    callback.log("Rust: Macro Finished".to_string());
}

// --- 7. JNI 接口 (保留高性能部分) ---

// 必须保留：用于接收 Java 传来的预览图（如果有）
#[no_mangle]
pub unsafe extern "C" fn Java_org_eu_freex_app_NativeLib_updateScreenBuffer(
    env: JNIEnv,
    _class: JClass,
    buffer: JByteBuffer,
    _w: i32,
    _h: i32,
    _stride: i32,
) {
    let addr = match env.get_direct_buffer_address(&buffer) {
        Ok(a) => a,
        Err(_) => return,
    };
    let len = match env.get_direct_buffer_capacity(&buffer) {
        Ok(l) => l,
        Err(_) => return,
    };
    if let Ok(mut guard) = SCREEN_BUFFER.lock() {
        if !guard.0.is_empty() {
            let min_len = std::cmp::min(guard.0.len(), len);
            std::ptr::copy_nonoverlapping(guard.0.as_ptr(), addr, min_len);
        }
    }
}

// 必须保留：用于无障碍模式下的录屏推流
#[no_mangle]
pub unsafe extern "C" fn Java_org_eu_freex_app_NativeLib_pushScreenImage(
    env: JNIEnv,
    _class: JClass,
    buffer: JByteBuffer,
    width: i32,
    height: i32,
    _pixel_stride: i32,
    row_stride: i32,
    scale: f32,
) {
    let addr = match env.get_direct_buffer_address(&buffer) {
        Ok(a) => a,
        Err(_) => return,
    };
    let len = match env.get_direct_buffer_capacity(&buffer) {
        Ok(l) => l,
        Err(_) => return,
    };
    let src_slice = std::slice::from_raw_parts(addr, len);

    if let Ok(mut guard) = SCREEN_BUFFER.lock() {
        if guard.0.len() != len {
            guard.0.resize(len, 0);
        }
        guard.0.copy_from_slice(src_slice);
        guard.1 = width as usize;
        guard.2 = height as usize;
        guard.3 = row_stride as usize;
        guard.4 = scale;
    }
}
