use serde::{Deserialize, Serialize};
use ts_rs::TS;

use super::ImageFilter;
use anyhow::{Ok, Result};
use image::{DynamicImage, GrayImage, Luma, Rgba, RgbaImage};

// [新增] 模式枚举
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, uniffi::Enum, TS)]
pub enum PosterizationMode {
    Rgb,
    Hsv,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record, TS)]
pub struct PosterizationFilter {
    pub mode: PosterizationMode,
    pub is_multi_value: bool,
    pub level: i32,
    pub channel1: bool,
    pub channel2: bool,
    pub channel3: bool,
}

impl ImageFilter for PosterizationFilter {
    fn apply(&self, img: &DynamicImage) -> Result<DynamicImage> {
        let rgb = img.to_rgb8();
        let (w, h) = rgb.dimensions();

        // 1. 准备 LUT (如果是多值化模式)
        let lut = if self.is_multi_value {
            let mut t = [0u8; 256];
            let levels = self.level.max(2) as f32;
            let step = if levels > 1.0 {
                255.0 / (levels - 1.0)
            } else {
                255.0
            };
            for i in 0..=255 {
                let v = i as f32;
                let q = (v / step).round() * step;
                t[i] = q.clamp(0.0, 255.0) as u8;
            }
            Some(t)
        } else {
            None
        };

        // 2. 准备输出容器
        // 多值化输出彩色(RGBA)，通道提取输出灰度(Luma)
        // 为了代码简洁，我们这里分两个大循环写，或者用闭包

        if let Some(lut) = lut {
            // --- 模式 A: 彩色多值化 (RGB / HSV 量化) ---
            let mut out = RgbaImage::new(w, h);

            for (x, y, pixel) in rgb.enumerate_pixels() {
                let (v1, v2, v3) = match self.mode {
                    PosterizationMode::Rgb => (pixel[0], pixel[1], pixel[2]),
                    PosterizationMode::Hsv => rgb_to_hsv(pixel[0], pixel[1], pixel[2]),
                };

                // 量化
                let q1 = lut[v1 as usize];
                let q2 = lut[v2 as usize];
                let q3 = lut[v3 as usize];

                // 如果是 HSV 模式，量化完还得转回 RGB 显示给用户看
                let (r, g, b) = match self.mode {
                    PosterizationMode::Rgb => (q1, q2, q3),
                    PosterizationMode::Hsv => hsv_to_rgb(q1, q2, q3),
                };

                out.put_pixel(x, y, Rgba([r, g, b, 255]));
            }
            Ok(DynamicImage::ImageRgba8(out))
        } else {
            // --- 模式 B: 通道提取 ---
            let mut out = GrayImage::new(w, h);
            let c1_on = self.channel1;
            let c2_on = self.channel2;
            let c3_on = self.channel3;

            for (x, y, pixel) in rgb.enumerate_pixels() {
                // 根据模式获取三个分量
                let (v1, v2, v3) = match self.mode {
                    PosterizationMode::Rgb => (pixel[0], pixel[1], pixel[2]),
                    // 转换到 HSV: v1=H, v2=S, v3=V
                    PosterizationMode::Hsv => rgb_to_hsv(pixel[0], pixel[1], pixel[2]),
                };

                let v1 = v1 as i16;
                let v2 = v2 as i16;
                let v3 = v3 as i16;

                let val: u8 = match (c1_on, c2_on, c3_on) {
                    // 单通道
                    (true, false, false) => v1 as u8,
                    (false, true, false) => v2 as u8,
                    (false, false, true) => v3 as u8,

                    // 双通道差分 (这是最强的功能)
                    // RGB模式: |R-G| 等
                    // HSV模式: |H-S| (通常用来找特定饱和度的特定颜色), 或者 |S-V|
                    (true, true, false) => (v1 - v2).abs() as u8,
                    (true, false, true) => (v1 - v3).abs() as u8,
                    (false, true, true) => (v2 - v3).abs() as u8,

                    _ => ((v1 + v2 + v3) / 3) as u8,
                };
                out.put_pixel(x, y, Luma([val]));
            }
            Ok(DynamicImage::ImageLuma8(out))
        }
    }
}

/// 将 HSV (0-255) 转回 RGB (用于预览)
#[inline]
fn hsv_to_rgb(h: u8, s: u8, v: u8) -> (u8, u8, u8) {
    let h = (h as f32 / 255.0) * 360.0;
    let s = s as f32 / 255.0;
    let v = v as f32 / 255.0;

    let c = v * s;
    let x = c * (1.0 - ((h / 60.0) % 2.0 - 1.0).abs());
    let m = v - c;

    let (r1, g1, b1) = if h < 60.0 {
        (c, x, 0.0)
    } else if h < 120.0 {
        (x, c, 0.0)
    } else if h < 180.0 {
        (0.0, c, x)
    } else if h < 240.0 {
        (0.0, x, c)
    } else if h < 300.0 {
        (x, 0.0, c)
    } else {
        (c, 0.0, x)
    };

    (
        ((r1 + m) * 255.0) as u8,
        ((g1 + m) * 255.0) as u8,
        ((b1 + m) * 255.0) as u8,
    )
}

// --- 辅助算法: 极速整数版 RGB <-> HSV ---

/// 将 RGB (0-255) 转换为 HSV (0-255)
/// H: 0-255 对应 0-360度
/// S: 0-255 对应 0-100%
/// V: 0-255 对应 0-100%
#[inline]
fn rgb_to_hsv(r: u8, g: u8, b: u8) -> (u8, u8, u8) {
    let r = r as f32;
    let g = g as f32;
    let b = b as f32;

    let max = r.max(g).max(b);
    let min = r.min(g).min(b);
    let delta = max - min;

    // V (Value)
    let v = max;

    // S (Saturation)
    let s = if max == 0.0 {
        0.0
    } else {
        (delta / max) * 255.0
    };

    // H (Hue)
    let h = if delta == 0.0 {
        0.0
    } else {
        let temp = if max == r {
            (g - b) / delta + (if g < b { 6.0 } else { 0.0 })
        } else if max == g {
            (b - r) / delta + 2.0
        } else {
            (r - g) / delta + 4.0
        };
        temp * 60.0
    };

    // 将 H (0-360) 映射到 0-255
    let h_u8 = (h / 360.0 * 255.0) as u8;
    let s_u8 = s as u8;
    let v_u8 = v as u8;

    (h_u8, s_u8, v_u8)
}
