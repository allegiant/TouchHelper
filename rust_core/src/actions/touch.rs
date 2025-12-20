use crate::types::PlatformCallback;
use crate::{core, input::InputController};
use serde::{Deserialize, Serialize};
use std::{thread, time};
use ts_rs::TS;

#[derive(Serialize, Deserialize, Debug, TS)]
#[ts(export)]
#[serde(tag = "type")]
pub enum TouchAction {
    Click {
        x: i32,
        y: i32,
        #[serde(default = "default_delay")]
        delay_ms: u64,
    },
    Swipe {
        points: Vec<Vec<i32>>,
        duration_ms: u64,
    },
}

fn default_delay() -> u64 {
    100
}

pub fn handle(action: &TouchAction, controller: &dyn InputController) {
    match action {
        TouchAction::Click { x, y, delay_ms } => {
            // 调用 core 的点击逻辑
            let (real_x, real_y) = core::map_coordinates(*x, *y);
            controller.click(real_x, real_y);
            thread::sleep(time::Duration::from_millis(*delay_ms));
        }
        TouchAction::Swipe {
            points,
            duration_ms,
        } => {
            controller.swipe(points, *duration_ms);
        }
    }
}
