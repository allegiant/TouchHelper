use rquickjs::class::Trace;
use rquickjs::JsLifetime;

use crate::domain::system::root_service::{find_color_helper, parse_hex_color};

// 1. 定义结构体 (保留 class 宏以注册元数据)
#[derive(Trace, JsLifetime)]
#[rquickjs::class]
pub struct Colors {}

// 4. Rust 内部构造
impl Colors {
    pub fn new() -> Self {
        Self {}
    }
}

// 5. JS 方法定义
#[rquickjs::methods]
impl Colors {
    // 🔥 核心修复：使用 #[qjs(constructor)]
    #[qjs(constructor)]
    pub fn ctor() -> Self {
        Self {}
    }

    // 🔥 核心修复：使用 #[qjs(rename = "...")]
    #[qjs(rename = "findColor")]
    pub fn find_color(&self, color: String) -> bool {
        let target = parse_hex_color(&color);
        find_color_helper(target, 10, None).is_some()
    }

    #[qjs(rename = "findColorPoint")]
    pub fn find_color_point(&self, color: String) -> Option<Vec<i32>> {
        let target = parse_hex_color(&color);
        if let Some((x, y)) = find_color_helper(target, 10, None) {
            Some(vec![x, y])
        } else {
            None
        }
    }
}
