// 📢 负责向 App 发送日志、状态更新
#[uniffi::export(callback_interface)]
pub trait PlatformLogger: Send + Sync {
    fn log(&self, msg: String);
    // 未来可以加: fn show_toast(&self, msg: String);
}

// ✋ 负责执行无障碍动作 (仅无障碍模式需要)
#[uniffi::export(callback_interface)]
pub trait AccessibilityService: Send + Sync {
    fn dispatch_click(&self, x: i32, y: i32);
    // 未来可以加: fn dispatch_swipe(...);
}
