use core::time;
use std::thread;

use log::info;
use serde::{Deserialize, Serialize};
use ts_rs::TS;

use crate::{
    core::{
        find_color_in_buffer, parse_hex_color, perform_root_click_cmd, start_root_server_internal,
        SCREEN_BUFFER,
    },
    logger::init_android_logger,
};

// 🔥 启用 UniFFI
uniffi::setup_scaffolding!();

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
pub enum Action {
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
pub struct MacroConfig {
    loop_count: i32,
    use_root: bool,
    actions: Vec<Action>,
}

// 🔥 修复：参数改为接收 callback，不再需要 JNIEnv
pub fn perform_click(callback: &Box<dyn PlatformCallback>, use_root: bool, x: i32, y: i32) {
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

#[uniffi::export]
pub fn start_core_root_server(jar_path: String) {
    init_android_logger();
    start_root_server_internal(jar_path);
}

#[uniffi::export]
pub fn run_core_macro(config_json: String, callback: Box<dyn PlatformCallback>) {
    init_android_logger();

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
