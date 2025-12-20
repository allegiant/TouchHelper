use std::{collections::HashMap, sync::Mutex, thread};

use log::info;

use crate::{
    input::{AccessibilityStrategy, InputController, RootStrategy},
    js_engine,
    logger::init_android_logger,
    types::{AccessibilityService, PlatformLogger},
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

// ==========================================
// 1. 全局状态存储
// ==========================================

lazy_static::lazy_static! {
    // 硬件控制器 (Root/无障碍)
    pub static ref CONTROLLER: Mutex<Option<Box<dyn InputController>>> = Mutex::new(None);

    // 配置池 (Vue 写, JS 读)
    pub static ref CONFIG_STORE: Mutex<HashMap<String, String>> = Mutex::new(HashMap::new());
}

// 内部辅助函数：给 JS 引擎读取配置用
pub fn internal_get_config(key: &str) -> Option<String> {
    let store = CONFIG_STORE.lock().unwrap();
    store.get(key).cloned()
}

// ==========================================
// 2. 对外 API (Kotlin 调用)
// ==========================================

/// 初始化服务 (App 启动时调用)
#[uniffi::export]
pub fn init_service(
    use_root: bool,
    logger: Box<dyn PlatformLogger>,
    service: Option<Box<dyn AccessibilityService>>,
) {
    init_android_logger();

    let ctrl: Box<dyn InputController> = if use_root {
        info!("Initializing Root Strategy");
        Box::new(RootStrategy)
    } else {
        info!("Initializing Accessibility Strategy");
        if let Some(s) = service {
            Box::new(AccessibilityStrategy::new(s))
        } else {
            logger.log("Error: Accessibility Service is required for non-root mode".into());
            return;
        }
    };

    let mut guard = CONTROLLER.lock().unwrap();
    *guard = Some(ctrl);
    logger.log(format!(
        "Service Initialized. Mode: {}",
        if use_root { "Root" } else { "Accessibility" }
    ));
}

/// 设置配置 (Vue v-model 绑定调用)
#[uniffi::export]
pub fn set_config(key: String, value: String) {
    info!("Config Set: {} = {}", key, value);
    let mut store = CONFIG_STORE.lock().unwrap();
    store.insert(key, value.clone());
}

/// 运行 JS 脚本 (点击开始按钮调用)
#[uniffi::export]
pub fn run_js_script(script_content: String) {
    // 开启新线程运行 Tokio + QuickJS，避免阻塞主线程
    thread::spawn(move || {
        // 创建 Tokio Runtime
        let rt = tokio::runtime::Builder::new_current_thread()
            .enable_all()
            .build()
            .unwrap();

        rt.block_on(async {
            match js_engine::run_script_async(script_content).await {
                Ok(_) => info!("✅ Script execution finished."),
                Err(e) => info!("❌ Script execution failed: {}", e),
            }
        });
    });
}
