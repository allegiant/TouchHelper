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

use crate::constants::{NATVIE_LIB_PATH, SERVER_CLASS_NAME, SHARED_FILE_PATH, SHARED_MEMORY_SIZE};

mod constants;

// #[cfg(test)] 的作用是：只有在运行 "cargo test" 时才编译这个文件。
// 打包 Android Release 包时，这个文件会被彻底忽略，不会增加体积。
#[cfg(test)]
mod export;

// --- 1. 全局变量 ---
lazy_static! {
    // 屏幕缓冲区：(像素数据, 宽, 高, 步长, 缩放比例)
    // 第5个字段 float 用于存储当前的缩放倍率，默认 1.0
    static ref SCREEN_BUFFER: Mutex<(Vec<u8>, usize, usize, usize, f32)> = Mutex::new((vec![], 0, 0, 0, 1.0));
}

// --- 2. 数据结构定义 ---
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

// --- 3. 辅助函数 ---

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

// 🔥 核心：点击时自动应用缩放比例，还原真实坐标
fn perform_click(env: &mut JNIEnv, use_root: bool, x: i32, y: i32) {
    // 1. 获取当前的 Scale
    let scale = {
        let guard = SCREEN_BUFFER.lock().unwrap();
        guard.4
    };

    // 2. 还原真实坐标 (小图坐标 * 缩放倍率)
    let real_x = (x as f32 * scale) as i32;
    let real_y = (y as f32 * scale) as i32;

    if use_root {
        let cmd = format!("input tap {} {}", real_x, real_y);
        let _ = Command::new("su").arg("-c").arg(cmd).output();
    } else {
        let class_name = NATVIE_LIB_PATH;
        let _ = env.call_static_method(
            class_name,
            "dispatchClick",
            "(II)V",
            &[JValue::Int(real_x), JValue::Int(real_y)],
        );
    }
}

// --- 4. 核心逻辑：Root Server 启动 (修复版) ---

fn start_root_server_internal(jar_path: String) {
    info!("Rust: 正在清理旧的 Java 进程...");
    // 杀死旧进程
    let _ = Command::new("su")
        .arg("-c")
        .arg(format!("pkill -f {}", SERVER_CLASS_NAME))
        .output();
    thread::sleep(time::Duration::from_millis(200));

    // 1. 🔥 修复点：委托 Root 权限创建共享内存文件 🔥
    // 我们不自己在 Rust 里 Create，而是让 su 去做
    info!("Rust: 委托 Root 创建共享内存文件...");
    let setup_cmd = format!(
        "touch {} && chmod 777 {} && truncate -s {} {}",
        SHARED_FILE_PATH, SHARED_FILE_PATH, SHARED_MEMORY_SIZE, SHARED_FILE_PATH
    );

    // 如果系统没有 truncate 命令，可以用 dd (Android通常有dd)
    // let setup_cmd = format!("dd if=/dev/zero of={} bs={} count=1 && chmod 777 {}", shared_file_path, buffer_size, shared_file_path);

    let setup_res = Command::new("su").arg("-c").arg(&setup_cmd).output();
    match setup_res {
        Ok(o) if o.status.success() => info!("Rust: 文件创建/权限设置成功"),
        _ => error!("Rust: ⚠️ 文件创建可能失败，后续 mmap 可能会出错"),
    }

    // 2. 启动 Java Server
    info!("Rust: 启动 Java Server, Jar: {}", jar_path);
    // 注意：这里需要传入 .so 的路径给 Java，否则 Java 里的 System.load 可能找不到库
    // 假设 so 在 /data/data/org.eu.freex.autogm/lib/libauto_gm.so
    // 你可能需要通过 JNI 把 packageCodePath 传进来，或者暂时写死
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

    // 🔥 防御性处理：如果 su 启动失败，不要 Panic
    let mut child = match child {
        Ok(c) => c,
        Err(e) => {
            error!("Rust: ❌ 无法启动 Root Server: {:?}", e);
            return;
        }
    };

    let stdout = child.stdout.take().expect("Failed stdout");
    let stderr = child.stderr.take().expect("Failed stderr");

    // 线程 1: 错误监听 (Stderr)
    thread::spawn(move || {
        let reader = BufReader::new(stderr);
        for line in reader.lines() {
            if let Ok(l) = line {
                error!("🔴 Java Stderr: {}", l);
            }
        }
    });

    // 线程 2: MMAP 数据读取
    thread::spawn(move || {
        let _keep_alive = child; // 保持子进程句柄，防止被回收
        thread::sleep(time::Duration::from_millis(500)); // 给 Java 一点启动时间

        // 🔥 修复点：只以 Read/Write 模式打开，不 Create，也不 Truncate 🔥
        // 这样如果文件不存在，它会返回 Err，而不是让 App 崩溃
        let file = match OpenOptions::new()
            .read(true)
            .write(true) // App 需要写吗？通常是 Java 写，Rust 读。如果 Rust 不写，可以去掉 write(true)
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

        // 尝试映射
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

        // ... 下面保持原有的 loop 读取逻辑不变 ...
        let mut reader = BufReader::new(stdout);
        let mut signal = [0u8; 1];

        loop {
            match reader.read_exact(&mut signal) {
                Ok(_) => {
                    if signal[0] == 0xAA {
                        // ... 这里是你原来的读取 buffer 代码，保持不变 ...
                        // 为了节省篇幅，这里略过，直接复制你原来的逻辑即可

                        // 1. 读宽高
                        if mmap.len() < 12 {
                            continue;
                        }
                        let w_bytes: [u8; 4] = mmap[0..4].try_into().unwrap();
                        let h_bytes: [u8; 4] = mmap[4..8].try_into().unwrap();
                        let width = u32::from_be_bytes(w_bytes) as usize;
                        let height = u32::from_be_bytes(h_bytes) as usize;

                        // 2. 读 Scale
                        let s_bytes: [u8; 4] = mmap[8..12].try_into().unwrap();
                        let scale = f32::from_be_bytes(s_bytes);

                        let frame_size = width * height * 4;
                        if mmap.len() < 12 + frame_size {
                            continue;
                        }

                        let pixels = &mmap[12..12 + frame_size];

                        // 更新全局锁
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

// --- 5. JNI 接口 ---

#[no_mangle]
pub unsafe extern "C" fn Java_org_eu_freex_app_NativeLib_startRootServer(
    mut env: JNIEnv,
    _class: JClass,
    jar_path_jstr: JString,
    _w: i32,
    _h: i32,
) {
    init_logger();
    let jar_path: String = match env.get_string(&jar_path_jstr) {
        Ok(s) => s.into(),
        Err(_) => return,
    };
    start_root_server_internal(jar_path);
}

#[no_mangle]
pub unsafe extern "C" fn Java_org_eu_freex_app_NativeLib_updateScreenBuffer(
    env: JNIEnv,
    _class: JClass,
    buffer: JByteBuffer,
    _w: i32,
    _h: i32,
    _stride: i32,
) {
    // 回显/预览接口
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

// 录屏模式入口：增加 scale 参数
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

#[no_mangle]
pub unsafe extern "C" fn Java_org_eu_freex_app_NativeLib_runMacro(
    mut env: JNIEnv,
    _class: JClass,
    config_json: JString,
) -> jstring {
    init_logger();
    let config_str: String = match env.get_string(&config_json) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("Error").unwrap().into_raw(),
    };

    let config: MacroConfig = match serde_json::from_str(&config_str) {
        Ok(c) => c,
        Err(e) => return env.new_string(format!("Error: {}", e)).unwrap().into_raw(),
    };

    let mut log_acc = String::new();
    let use_root = config.use_root;

    for _ in 0..config.loop_count {
        for action in &config.actions {
            match action {
                Action::Click { x, y, delay_ms } => {
                    perform_click(&mut env, use_root, *x, *y);
                    thread::sleep(time::Duration::from_millis(*delay_ms));
                }
                Action::Wait { ms } => {
                    thread::sleep(time::Duration::from_millis(*ms));
                }
                Action::Log { msg } => {
                    info!("Macro Log: {}", msg);
                    log_acc.push_str(&format!("{}\n", msg));
                }
                Action::FindAndClick {
                    color_html,
                    tolerance,
                    region,
                } => {
                    let target_rgb = parse_hex_color(color_html);
                    let mut found_pos = None;
                    let mut debug_color = (0, 0, 0);
                    let mut final_scale = 1.0; // 用于后续点击还原

                    {
                        let guard = SCREEN_BUFFER.lock().unwrap();
                        let pixels = &guard.0;
                        let w = guard.1;
                        let h = guard.2;
                        let stride = guard.3;
                        let scale = guard.4; // 获取当前的缩放比例
                        final_scale = scale;

                        if !pixels.is_empty() {
                            // 🔥🔥🔥 核心修复：将 JSON 里的原始坐标映射到缩放后的坐标系 🔥🔥🔥
                            let rect = region
                                .clone()
                                .map(|r| {
                                    // 坐标 / scale
                                    let sx = (r[0] as f32 / scale) as usize;
                                    let sy = (r[1] as f32 / scale) as usize;
                                    let sw = (r[2] as f32 / scale) as usize;
                                    let sh = (r[3] as f32 / scale) as usize;
                                    (sx, sy, sw, sh)
                                })
                                .unwrap_or((0, 0, w, h));

                            // Debug: 看看映射后的区域对不对
                            // info!("Rust: 原始区域 {:?}, 缩放后区域 {:?}", region, rect);

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
                        // 还原回真实坐标用于打印日志，方便你调试
                        let real_x = (x as f32 * final_scale) as i32;
                        let real_y = (y as f32 * final_scale) as i32;

                        info!(
                            "Rust: ✅ 找到颜色 {} @ 小图({}, {}) -> 原图({}, {})",
                            color_html, x, y, real_x, real_y
                        );

                        // perform_click 内部会自动处理缩放，这里不用管，直接把找到的小图坐标传进去即可
                        // 等等！perform_click 内部是重新获取 scale 计算的，为了保证原子性，这是对的。
                        // 但是为了保险，我们这里既然已经算出了 real_x/y，其实可以直接点击 real_x/y。
                        // 不过为了保持代码结构一致性，我们继续调用 perform_click，传入小图坐标 (x, y)
                        perform_click(&mut env, use_root, x, y);
                    } else {
                        info!(
                            "Rust: ❌ 未找到 {}. 起点颜色: #{:02X}{:02X}{:02X}",
                            color_html, debug_color.0, debug_color.1, debug_color.2
                        );
                    }
                }
            }
        }
    }
    env.new_string(log_acc).unwrap().into_raw()
}
