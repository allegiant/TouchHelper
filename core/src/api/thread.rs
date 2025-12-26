// ==========================================================
// 1. Thread 类 (线程操作)
// JS 使用: Thread.sleep(1000)
// ==========================================================

use std::{sync::atomic::Ordering, time::Duration};

use rquickjs::{class::Trace, JsLifetime};

use crate::uniffi_binding::IS_PAUSED;

#[derive(Trace, JsLifetime)]
#[rquickjs::class]
pub struct Thread {}

impl Thread {
    pub fn new() -> Self {
        Self {}
    }
}

#[rquickjs::methods]
impl Thread {
    /// 构造函数 (虽然我们通常用全局实例)
    #[qjs(constructor)]
    pub fn ctor() -> Self {
        Self {}
    }
    /// Sleep (异步操作)
    #[qjs(rename = "sleep")]
    pub async fn sleep(ms: u64) {
        // 1. 执行正常的休眠
        tokio::time::sleep(Duration::from_millis(ms)).await;
        // 2. 暂停检查 (原子读取)
        // 🔥 这里直接读取原子变量，性能极高
        while IS_PAUSED.load(Ordering::Relaxed) {
            tokio::time::sleep(Duration::from_millis(200)).await;
        }
    }
}
