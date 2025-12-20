use crate::core::{find_color_helper, map_coordinates, parse_hex_color};
use crate::input::InputController;
use crate::types::PlatformCallback;
use serde::{Deserialize, Serialize};
use ts_rs::TS;

#[derive(Serialize, Deserialize, Debug, TS)]
#[ts(export)]
#[serde(tag = "type")]
pub enum VisionAction {
    FindColor {
        color_html: String,
        tolerance: u8,
        region: Option<Vec<i32>>,
        auto_click: bool,
    },
    FindImage {
        image_name: String,
        threshold: f32,
        region: Option<Vec<i32>>,
        auto_click: bool,
    },
}

pub fn handle(
    action: &VisionAction,
    callback: &Box<dyn PlatformCallback>,
    controller: &dyn InputController,
) {
    match action {
        VisionAction::FindColor {
            color_html,
            tolerance,
            region,
            auto_click,
        } => {
            let target_rgb = parse_hex_color(color_html);

            let found_pos = find_color_helper(target_rgb, *tolerance, region.clone());

            if let Some((x, y)) = found_pos {
                let (real_x, real_y) = map_coordinates(x, y);

                let log_msg = format!(
                    "Rust: ✅ 找到颜色 {} @ 小图({}, {}) -> 原图({}, {})",
                    color_html, x, y, real_x, real_y
                );
                callback.log(log_msg);
                if *auto_click {
                    controller.click(real_x, real_y);
                }
            } else {
                let log_msg = format!("Rust: ❌ 未找到 {}.", color_html);
                callback.log(log_msg);
            }
        }
        VisionAction::FindImage { image_name, .. } => {
            callback.log(format!("TODO: FindImage {}", image_name));
        }
    }
}
