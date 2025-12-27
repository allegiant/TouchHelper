use std::{
    sync::{
        atomic::{AtomicBool, Ordering},
        Mutex,
    },
    thread,
};

use log::info;

use crate::{
    input::{AccessibilityStrategy, InputController, RootStrategy},
    js_engine::{self, CURRENT_SCRIPT_TASK},
    logger::init_logger,
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

pub static IS_PAUSED: AtomicBool = AtomicBool::new(false);

lazy_static::lazy_static! {
    // 硬件控制器 (Root/无障碍)
    pub static ref CONTROLLER: Mutex<Option<Box<dyn InputController>>> = Mutex::new(None);
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
    init_logger();

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

/// 运行 JS 脚本 (点击开始按钮调用)
#[uniffi::export]
pub fn run_js_script(script_content: String) {
    // 1. 先停止旧脚本
    stop_script();

    // 2. 启动新线程
    thread::spawn(move || {
        let rt = tokio::runtime::Builder::new_current_thread()
            .enable_all()
            .build()
            .unwrap();

        rt.block_on(async move {
            // 3. 启动任务
            let handle = tokio::spawn(async move {
                // 🔥 每次运行前，强制重置为非暂停状态
                IS_PAUSED.store(false, Ordering::Relaxed);

                match js_engine::run_script_async(script_content).await {
                    Ok(_) => info!("✅ Script finished successfully"),
                    Err(e) => info!("❌ Script error: {}", e),
                }
            });

            // 4. 获取 AbortHandle 并存入全局变量
            // 🔥 关键修改：使用 handle.abort_handle()
            let abort_handle = handle.abort_handle();

            {
                let task_mutex = CURRENT_SCRIPT_TASK.get_or_init(|| std::sync::Mutex::new(None));
                if let Ok(mut guard) = task_mutex.lock() {
                    *guard = Some(abort_handle); // AbortHandle 可以被 Clone (虽然这里是 Move 进去，但也支持 clone)
                }
            }

            // 5. 等待任务结束
            // 无论是自然结束，还是被外部 abort()，这里都会返回
            // 如果是被 abort 的，result 会是一个 Cancelled Error
            let _ = handle.await;

            // 6. 清理全局变量
            {
                let task_mutex = CURRENT_SCRIPT_TASK.get_or_init(|| std::sync::Mutex::new(None));
                if let Ok(mut guard) = task_mutex.lock() {
                    *guard = None;
                }
            }

            info!("👋 Script Task Ended, Runtime shutting down.");
        });
    });
}

#[uniffi::export]
pub fn stop_script() {
    let task_mutex = CURRENT_SCRIPT_TASK.get_or_init(|| std::sync::Mutex::new(None));

    if let Ok(mut guard) = task_mutex.lock() {
        // 取出 AbortHandle
        if let Some(abort_handle) = guard.take() {
            info!("🛑 Stopping script task...");
            abort_handle.abort(); // 🔥 使用 abort_handle 停止任务
            info!("✅ Script task aborted signal sent.");
        } else {
            info!("⚠️ No running script to stop.");
        }
    }
}

#[uniffi::export]
pub fn set_paused(paused: bool) {
    info!("Script Paused State: {}", paused);
    // Relaxed 顺序对于这种简单的标志位已经足够了
    IS_PAUSED.store(paused, Ordering::Relaxed);
}
