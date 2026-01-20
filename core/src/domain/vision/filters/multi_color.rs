use super::ImageFilter;
use crate::domain::vision::colors; // 引用 colors 模块的辅助函数
use crate::domain::vision::types::ColorRule; // 引用 types 中的 ColorRule
use anyhow::Result;
use image::{DynamicImage, GrayImage, Luma, Rgba, RgbaImage};
use serde::{Deserialize, Serialize};
use ts_rs::TS;
// ==========================================
// 4. MultiColorFilter(颜色选取)
// ==========================================
#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record, TS)]
pub struct MultiColorFilter {
    // 颜色规则列表
    pub rules: Vec<ColorRule>,
    // 背景色/反色模式：勾选后，匹配到的颜色会被剔除，未匹配的保留
    pub is_invert: bool,
    // 颜色选留：勾选后保留原色，不勾选则二值化(白)
    pub keep_original: bool,
}

impl ImageFilter for MultiColorFilter {
    fn apply(&self, img: &DynamicImage) -> Result<DynamicImage> {
        // 预解析颜色
        let parsed_rules: Vec<([u8; 3], [u8; 3])> = self
            .rules
            .iter()
            .filter(|r| r.is_enabled)
            .map(|r| {
                (
                    colors::parse_hex(&r.target_hex),
                    colors::parse_hex(&r.bias_hex),
                )
            })
            .collect();

        let rgb = img.to_rgb8();
        let (w, h) = rgb.dimensions();

        if self.keep_original {
            let mut out = RgbaImage::new(w, h);
            for (x, y, pixel) in rgb.enumerate_pixels() {
                let p = pixel.0;
                let mut matched = false;
                for (target, bias) in &parsed_rules {
                    if colors::is_match(p, *target, *bias) {
                        matched = true;
                        break;
                    }
                }
                let should_keep = if self.is_invert { !matched } else { matched };
                if should_keep {
                    out.put_pixel(x, y, Rgba([p[0], p[1], p[2], 255]));
                } else {
                    out.put_pixel(x, y, Rgba([0, 0, 0, 255]));
                }
            }
            Ok(DynamicImage::ImageRgba8(out))
        } else {
            let mut out = GrayImage::new(w, h);
            for (x, y, pixel) in rgb.enumerate_pixels() {
                let p = pixel.0;
                let mut matched = false;
                for (target, bias) in &parsed_rules {
                    if colors::is_match(p, *target, *bias) {
                        matched = true;
                        break;
                    }
                }
                let should_keep = if self.is_invert { !matched } else { matched };
                if should_keep {
                    out.put_pixel(x, y, Luma([255]));
                } else {
                    out.put_pixel(x, y, Luma([0]));
                }
            }
            Ok(DynamicImage::ImageLuma8(out))
        }
    }
}
