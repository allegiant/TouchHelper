// ==========================================================
// 1. Thread 类 (线程操作)
// JS 使用: Thread.sleep(1000)
// ==========================================================

use std::time::Duration;

use rquickjs::{class::Trace, JsLifetime};

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
        tokio::time::sleep(Duration::from_millis(ms)).await;
    }
}
