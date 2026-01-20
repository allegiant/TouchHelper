use std::collections::HashMap;

use anyhow::Ok;
use image::DynamicImage;
use serde::{Deserialize, Serialize};
use ts_rs::TS;

use crate::domain::vision::colors;

use super::ImageFilter;

// [新增] 自动裁剪模式
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, uniffi::Enum, TS)]
pub enum AutoCropMode {
    AutoCorners, // 自动探测角落
    FixedColor,  // 固定颜色
}

// [新增] 自动裁剪滤镜参数
#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record, TS)]
pub struct AutoCropFilter {
    pub mode: AutoCropMode,
    pub tolerance: f32,          // 对应 Kotlin 的 Float
    pub padding: i32,            // 留白
    pub noise_threshold: i32,    // 抗噪阈值
    pub fixed_color_hex: String, // 指定背景色 (例如 "#000000")
}

impl ImageFilter for AutoCropFilter {
    fn apply(&self, img: &image::DynamicImage) -> anyhow::Result<image::DynamicImage> {
        let rgba = img.to_rgba8();
        let (w, h) = rgba.dimensions();

        // 辅助：计算色差平方
        let color_diff_sq = |c1: [u8; 4], c2: [u8; 4]| -> i32 {
            // 如果两个像素 alpha 都很小，视为相同
            if c1[3] < 10 && c2[3] < 10 {
                return 0;
            }
            let r = c1[0] as i32 - c2[0] as i32;
            let g = c1[1] as i32 - c2[1] as i32;
            let b = c1[2] as i32 - c2[2] as i32;
            // 如果是固定背景色模式，我们通常忽略 Alpha 差异，除非背景本身是透明
            r * r + g * g + b * b
        };

        let tolerance_sq = (self.tolerance as i32).pow(2);

        // --- 核心策略：寻找一行/一列中的“主流颜色” (Mode Color) ---
        // 这比只看角落要稳健得多，可以忽略边框上的噪点
        let get_dominant_color = |pixels: Vec<[u8; 4]>| -> [u8; 4] {
            let mut counts: HashMap<[u8; 4], usize> = HashMap::new();
            for p in pixels {
                // 简单的量化处理，避免噪点被当做不同颜色
                // 这里为了性能直接统计，如果噪点多，其实 Mode 依然会是背景色
                *counts.entry(p).or_insert(0) += 1;
            }
            counts
                .into_iter()
                .max_by_key(|&(_, count)| count)
                .map(|(color, _)| color)
                .unwrap_or([0, 0, 0, 0]) // Fallback
        };

        // 2. 处理固定颜色模式
        let target_color = if self.mode == AutoCropMode::FixedColor {
            // 解析 Hex 字符串 (#RRGGBB)
            let [r, g, b] = colors::parse_hex(&self.fixed_color_hex);
            Some(image::Rgb([r, g, b]))
        } else {
            None
        };

        // 如果用户指定了固定颜色，则所有方向都用这个颜色
        let fixed_bg = target_color.map(|c| [c[0], c[1], c[2], 255]);

        // ===========================
        // 1. 扫描上边界 (Top)
        // ===========================
        let mut top = 0;
        let skip_step = 2; // 步长 skip_step 固定为 2 以提升性能/

        // 确定上边的参考背景色
        let top_bg = if let Some(c) = fixed_bg {
            c
        } else {
            // 采样第一行 (Row 0) 的所有像素，找出出现最多的颜色
            let mut sample = Vec::with_capacity(w as usize);

            // 步长 skip_step 固定为 2 以提升性能
            for x in (0..w).step_by(skip_step as usize) {
                sample.push(rgba.get_pixel(x, 0).0);
            }
            get_dominant_color(sample)
        };

        for y in (0..h).step_by(skip_step as usize) {
            let mut row_noise = 0;
            let mut found_content = false;
            for x in (0..w).step_by(skip_step as usize) {
                let p = rgba.get_pixel(x, y).0;
                // 判定逻辑：1. 透明度低是背景 2. 颜色接近参考色是背景
                let is_bg =
                    p[3] < 10 || (top_bg[3] >= 10 && color_diff_sq(p, top_bg) <= tolerance_sq);

                if !is_bg {
                    row_noise += 1;
                    if row_noise > self.noise_threshold {
                        found_content = true;
                        break;
                    }
                }
            }
            if found_content {
                top = y;
                break;
            }
        }

        // ===========================
        // 2. 扫描下边界 (Bottom)
        // ===========================
        let mut bottom = h;

        let bottom_bg = if let Some(c) = fixed_bg {
            c
        } else {
            // 采样最后一行
            let mut sample = Vec::with_capacity(w as usize);
            for x in (0..w).step_by(skip_step as usize) {
                sample.push(rgba.get_pixel(x, h - 1).0);
            }
            get_dominant_color(sample)
        };

        for y in (0..h).rev().step_by(skip_step as usize) {
            let mut row_noise = 0;
            let mut found_content = false;
            for x in (0..w).step_by(skip_step as usize) {
                let p = rgba.get_pixel(x, y).0;
                let is_bg = p[3] < 10
                    || (bottom_bg[3] >= 10 && color_diff_sq(p, bottom_bg) <= tolerance_sq);

                if !is_bg {
                    row_noise += 1;
                    if row_noise > self.noise_threshold {
                        found_content = true;
                        break;
                    }
                }
            }
            if found_content {
                bottom = y + 1;
                break;
            }
        }

        if top >= bottom {
            return Ok(img.clone());
        }

        // ===========================
        // 3. 扫描左边界 (Left)
        // ===========================
        let mut left = 0;

        let left_bg = if let Some(c) = fixed_bg {
            c
        } else {
            // 采样第一列 (Col 0)
            let mut sample = Vec::with_capacity(h as usize);
            for y in (0..h).step_by(skip_step as usize) {
                sample.push(rgba.get_pixel(0, y).0);
            }
            get_dominant_color(sample)
        };

        for x in (0..w).step_by(skip_step as usize) {
            let mut col_noise = 0;
            let mut found_content = false;
            // 只扫描 Top~Bottom 范围内的像素
            for y in (top..bottom).step_by(skip_step as usize) {
                let p = rgba.get_pixel(x, y).0;
                let is_bg =
                    p[3] < 10 || (left_bg[3] >= 10 && color_diff_sq(p, left_bg) <= tolerance_sq);

                if !is_bg {
                    col_noise += 1;
                    if col_noise > self.noise_threshold {
                        found_content = true;
                        break;
                    }
                }
            }
            if found_content {
                left = x;
                break;
            }
        }

        // ===========================
        // 4. 扫描右边界 (Right)
        // ===========================
        let mut right = w;

        let right_bg = if let Some(c) = fixed_bg {
            c
        } else {
            let mut sample = Vec::with_capacity(h as usize);
            for y in (0..h).step_by(skip_step as usize) {
                sample.push(rgba.get_pixel(w - 1, y).0);
            }
            get_dominant_color(sample)
        };

        for x in (0..w).rev().step_by(skip_step as usize) {
            let mut col_noise = 0;
            let mut found_content = false;
            for y in (top..bottom).step_by(skip_step as usize) {
                let p = rgba.get_pixel(x, y).0;
                let is_bg =
                    p[3] < 10 || (right_bg[3] >= 10 && color_diff_sq(p, right_bg) <= tolerance_sq);

                if !is_bg {
                    col_noise += 1;
                    if col_noise > self.noise_threshold {
                        found_content = true;
                        break;
                    }
                }
            }
            if found_content {
                right = x + 1;
                break;
            }
        }

        // 应用 Padding
        let crop_x = left.saturating_sub(self.padding as u32);
        let crop_y = top.saturating_sub(self.padding as u32);
        let crop_w = (right - left + self.padding as u32 * 2)
            .min(w - crop_x)
            .max(1);
        let crop_h = (bottom - top + self.padding as u32 * 2)
            .min(h - crop_y)
            .max(1);

        let cropped_buffer =
            image::imageops::crop_imm(img, crop_x, crop_y, crop_w, crop_h).to_image();
        Ok(DynamicImage::ImageRgba8(cropped_buffer))
    }
}
