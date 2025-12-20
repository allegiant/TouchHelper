use rquickjs::class::Trace;
use rquickjs::JsLifetime;

use crate::uniffi_binding::internal_get_config;

// ==========================================================
// 3. Config 类 (配置)
// JS 使用: Config.get("loop")
// ==========================================================
#[derive(Trace, JsLifetime)]
#[rquickjs::class]
pub struct Config {}

impl Config {
    pub fn new() -> Self {
        Self {}
    }
}

#[rquickjs::methods]
impl Config {
    #[qjs(constructor)]
    pub fn ctor() -> Self {
        Self {}
    }

    #[qjs(rename = "get")]
    pub fn get(&self, key: String) -> String {
        internal_get_config(&key).unwrap_or_default()
    }

    #[qjs(rename = "getInt")]
    pub fn get_int(&self, key: String) -> i32 {
        internal_get_config(&key)
            .unwrap_or("0".to_string())
            .parse::<i32>()
            .unwrap_or(0)
    }
}
