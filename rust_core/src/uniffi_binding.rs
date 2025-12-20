use log::info;
use serde::{Deserialize, Serialize};
use ts_rs::TS;

use crate::{
    actions::{control, device, touch, vision},
    core::start_root_server_internal,
    input::{AccessibilityStrategy, InputController, RootStrategy},
    logger::init_android_logger,
    types::PlatformCallback,
};

// ⚠️ UniFFI 的 callback 是 Box<dyn ...>，它是唯一的。
// 如果你想在两个地方用（Strategy里用一次，主循环里用一次），需要 RefCell 或 Arc。
// 这里的 Rust 所有权会比较棘手。
//
// 🔧 简易解决方案：
// 既然 AccessibilityStrategy 只有在非 Root 下才用，
// 而 Controller trait 主要是执行动作。
// 我们可以让 `run_macro` 始终持有 `callback` 用于日志，
// 而 `AccessibilityStrategy` 只负责 `dispatch_click`。
//
// 但 AccessibilityStrategy 内部实现依赖 callback。
// 这是一个经典的 Rust 借用检查难题。

// 🔥 启用 UniFFI
uniffi::setup_scaffolding!();

// 1. 顶层 Action 包装器
#[derive(Serialize, Deserialize, Debug, TS)]
#[ts(export)]
#[serde(tag = "module", content = "action")]
pub enum Action {
    Touch(touch::TouchAction),
    Vision(vision::VisionAction),
    Device(device::DeviceAction),
    Control(control::ControlAction),
}

#[derive(Serialize, Deserialize, Debug, TS)]
#[ts(export)]
pub struct MacroConfig {
    loop_count: i32,
    use_root: bool,
    actions: Vec<Action>,
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
            let msg = format!("JSON Error: {}", e);
            callback.log(msg);
            return;
        }
    };

    let controller: Box<dyn InputController> = if config.use_root {
        info!("Using Root Strategy");
        Box::new(RootStrategy)
    } else {
        info!("Using Accessibility Strategy");
        // 无障碍策略需要持有 Callback 的所有权或克隆
        // 由于 Box<dyn Trait> 很难克隆，我们这里需要特殊处理
        // 方案 A: 让 Callback 支持 Clone (比较麻烦)
        // 方案 B: 这里的 callback 已经被 move 进来了。
        // 如果 AccessibilityStrategy 拿走了 callback，那 Vision 模块要打印日志怎么办？

        // 💡 最佳实践：将 Controller 和 Logger 分离
        // 但为了简单，我们可以 clone callback 的引用，或者 wrap 进 Arc<Mutex<...>>
        // 考虑到 UniFFI 的限制，我们这里直接构造一个新的 Box
        Box::new(AccessibilityStrategy::new(callback_clone_hack(&callback)))
    };

    for _ in 0..config.loop_count {
        for action in &config.actions {
            // 🔥 路由分发：将 Action 派发给对应的处理模块
            match action {
                // 传入 &*controller (解引用 Box 得到 dyn Trait)
                Action::Control(cmd) => control::handle(cmd, &callback),
                Action::Touch(cmd) => touch::handle(cmd, &callback, &*controller),
                // Vision 可能既需要 Logger 又需要 Input
                Action::Vision(cmd) => vision::handle(cmd, &callback, &*controller),
                Action::Device(cmd) => device::handle(cmd, &callback, &*controller),
                Action::Control(cmd) => control::handle(cmd, &callback),
            }
        }
    }
    callback.log("Rust: Macro Finished".to_string());
}
