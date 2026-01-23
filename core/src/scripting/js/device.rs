// ==========================================================
// 1. Device 类 (硬件操作)
// JS 使用: Device.click(100, 100)
// ==========================================================

use rquickjs::{class::Trace, JsLifetime};
use rquickjs::{Ctx, Exception, Result};

use crate::domain::system::root_service::{self, map_coordinates};

use super::session::JsImage;
use super::with_controller;

#[derive(Trace, JsLifetime)]
#[rquickjs::class]
pub struct Device {}

impl Device {
    pub fn new() -> Self {
        Self {}
    }
}

#[rquickjs::methods]
impl Device {
    #[qjs(constructor)]
    pub fn ctor() -> Self {
        Self {}
    }

    // 🔥 核心实现：capture()
    #[qjs(rename = "capture")]
    pub fn capture<'js>(&self, ctx: Ctx<'js>) -> Result<JsImage> {
        // 1. 调用 RootService 获取当前屏幕截图
        // 注意：RootService::capture_screen() 通常返回 Result<DynamicImage>
        // 你可能需要根据你实际的 RootService 实现来调整这里
        match root_service::capture_screen() {
            Ok(dynamic_image) => Ok(JsImage::new(dynamic_image)),
            Err(e) => Self::throw_err(&ctx, &format!("capture failed: {}", e)),
        }
    }

    #[qjs(rename = "click")]
    pub fn click(&self, x: i32, y: i32) {
        with_controller(|ctrl| {
            let (rx, ry) = map_coordinates(x, y);
            ctrl.click(rx, ry);
        });
    }

    pub fn swipe(&self, x1: i32, y1: i32, x2: i32, y2: i32, duration: u64) {
        with_controller(|ctrl| {
            let (rx1, ry1) = map_coordinates(x1, y1);
            let (rx2, ry2) = map_coordinates(x2, y2);
            let mut points = Vec::new();
            points.push(vec![rx1, ry1]);
            points.push(vec![rx2, ry2]);
            ctrl.swipe(&points, duration);
        });
    }

    pub fn shell(&self, cmd: String) {
        with_controller(|ctrl| {
            ctrl.shell(cmd.as_str());
        });
    }
}

impl Device {
    /// 辅助方法：抛出 JS 异常并返回 rquickjs::Result<T>
    /// 返回值永远是 Err(Error)，所以泛型 T 可以是任意类型
    fn throw_err<'js, T>(ctx: &Ctx<'js>, msg: &str) -> Result<T> {
        let ex = Exception::from_message(ctx.clone(), msg)?;

        // 🔴 修复点：
        // 1. ex.into() 将 Exception 转换为 Value
        // 2. ctx.throw() 返回 Error (表示 JS 异常已设置)
        // 3. 将其包裹在 Result::Err 中
        Err(ctx.throw(ex.into()))
    }
}
