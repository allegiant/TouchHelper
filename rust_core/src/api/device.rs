// ==========================================================
// 1. Device 类 (硬件操作)
// JS 使用: Device.click(100, 100)
// ==========================================================

use rquickjs::{class::Trace, JsLifetime};

use crate::{api::with_controller, core::map_coordinates};

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
