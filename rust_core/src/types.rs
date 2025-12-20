// --- 2. UniFFI 回调接口 ---
// 让 Kotlin 实现这个接口，用于接收日志和点击指令
#[uniffi::export(callback_interface)]
pub trait PlatformCallback: Send + Sync {
    fn dispatch_click(&self, x: i32, y: i32);
    fn log(&self, msg: String);
}
