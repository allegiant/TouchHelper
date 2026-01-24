use std::collections::HashMap;

use anyhow::Ok;
use image::{DynamicImage, Rgba};
use imageproc::geometric_transformations::{rotate_about_center, Interpolation};
use serde::{Deserialize, Serialize};
use ts_rs::TS;

use super::ImageFilter;

/// 13. 旋转纠正 (Rotation Correction)
/// 包含：手动旋转 + 自动纠偏检测
/// - is_auto: 是否启用自动检测
/// - manual_angle: 手动指定的角度
/// - max_search_angle: 自动模式下的最大搜索范围 (例如 30.0 度)
/// - precision: 自动模式下的搜索步长 (例如 0.5 度)
#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record, TS)]
#[serde(rename_all = "camelCase")]
#[ts(rename_all = "camelCase")]
pub struct RotationFilter {
    pub is_auto: bool,
    pub manual_angle: f64,     // 手动模式角度
    pub max_search_angle: f64, // 自动模式：最大搜索范围 (如 30.0)
    pub precision: f64,        // 自动模式：精度步长 (如 0.5)
}

impl ImageFilter for RotationFilter {
    fn apply(&self, img: &image::DynamicImage) -> anyhow::Result<image::DynamicImage> {
        let angle_to_rotate = if self.is_auto {
            // 自动计算最佳角度
            detect_skew_by_projection(img, self.max_search_angle, self.precision)
        } else {
            self.manual_angle
        };

        // 如果角度很小，直接返回原图，避免重采样带来的模糊
        if angle_to_rotate.abs() < 0.1 {
            return Ok(img.clone());
        }

        // 执行旋转
        // 为了保持图片尺寸不变或适应旋转，这里使用 imageproc 的 rotate_about_center
        // 它会保持原图尺寸，超出部分会被裁剪，空出部分填背景色
        // 如果需要保留所有内容（扩大画布），需要自己计算新尺寸。
        // 这里针对字库制作，通常使用“中心旋转”即可。

        let rgba = img.to_rgba8();
        // 填充背景色：假设是制作字库，通常背景是透明或白色。这里使用透明。
        let bg = Rgba([0, 0, 0, 0]);

        let rad = angle_to_rotate.to_radians() as f32;
        let rotated = rotate_about_center(&rgba, rad, Interpolation::Bilinear, bg);

        Ok(DynamicImage::ImageRgba8(rotated))
    }
}

/// [私有辅助] 核心算法：检测图像倾斜角度 (基于投影方差法)
/// 性能优化：不旋转图像，只进行坐标映射计算投影
fn detect_skew_by_projection(img: &DynamicImage, max_angle: f64, step: f64) -> f64 {
    let gray = img.to_luma8();
    let (w, h) = gray.dimensions();

    // 1. 提取前景点 (简化版：假设亮度 < 128 为文字/前景)
    // 为了性能，可以先 resize 到小图计算，或者只采样部分点
    let mut points = Vec::new();
    let skip = 2; // 降采样以提升速度
    for y in (0..h).step_by(skip) {
        for x in (0..w).step_by(skip) {
            if gray.get_pixel(x, y)[0] < 128 {
                points.push((x as f64, y as f64));
            }
        }
    }

    if points.is_empty() {
        return 0.0;
    }

    let mut best_angle = 0.0;
    let mut max_variance = -1.0;

    // 2. 暴力搜索 / 细分搜索
    // 从 -max 到 +max
    let mut angle = -max_angle;
    while angle <= max_angle {
        let rad = angle.to_radians();
        let sin_a = rad.sin();
        let cos_a = rad.cos();

        // 计算该角度下的水平投影
        // 旋转公式: y' = x * sin(theta) + y * cos(theta)
        // 我们只关心旋转后的 Y 坐标，因为我们要看“行”是否对其
        let mut projection_buckets: HashMap<i32, u32> = HashMap::new();

        for (x, y) in &points {
            let y_projected = (x * sin_a + y * cos_a).round() as i32;
            *projection_buckets.entry(y_projected).or_insert(0) += 1;
        }

        // 计算方差: 方差越大，说明行与行分界越明显（文字越直）
        let values: Vec<u32> = projection_buckets.values().cloned().collect();
        let variance = calculate_variance(&values);

        if variance > max_variance {
            max_variance = variance;
            best_angle = angle;
        }

        angle += step;
    }

    best_angle
}

fn calculate_variance(data: &[u32]) -> f64 {
    if data.is_empty() {
        return 0.0;
    }
    let sum: u32 = data.iter().sum();
    let mean = sum as f64 / data.len() as f64;

    let sum_sq_diff: f64 = data
        .iter()
        .map(|&x| {
            let diff = x as f64 - mean;
            diff * diff
        })
        .sum();

    sum_sq_diff / data.len() as f64
}
