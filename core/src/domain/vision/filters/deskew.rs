use anyhow::Ok;
use image::{DynamicImage, GrayImage, Luma};
use serde::{Deserialize, Serialize};
use ts_rs::TS;

use super::ImageFilter;

/// 13. 倾斜矫正 (Deskew)
/// 支持自动检测角度或手动指定角度旋转。
///
/// * `img`: 输入图像
/// * `angle`: 手动旋转角度 (度)。如果 `auto` 为 true，此值将被忽略（或作为微调）。
/// * `auto`: 是否自动检测倾斜角。
/// * `background_color`: 旋转后空白区域的填充色 (通常为黑色 0 或白色 255)
#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record, TS)]
#[serde(rename_all = "camelCase")]
#[ts(rename_all = "camelCase")]
pub struct DeskewFilter {
    pub angle: f32, // 输入的角度 (Degrees)
    pub auto: bool,
    pub background_color: u8,
}

impl ImageFilter for DeskewFilter {
    fn apply(&self, img: &image::DynamicImage) -> anyhow::Result<image::DynamicImage> {
        let gray = img.to_luma8();
        let mut final_angle = self.angle;

        // 如果开启自动检测
        if self.auto {
            let detected_angle = detect_skew_hough(&gray);
            // 限制自动检测的范围，防止误判造成剧烈旋转（通常矫正范围在 +/- 20度以内）
            if detected_angle > -20.0 && detected_angle < 20.0 {
                final_angle = detected_angle;
            }
        }

        // 如果角度很小，直接返回原图，节省性能
        if final_angle.abs() < 0.1 {
            return Ok(img.clone());
        }

        // 执行旋转
        // imageproc 的 rotate_about_center 会保持原图尺寸，多余部分填黑
        // 为了更好的效果，这里我们使用 image 库的 interpolate 旋转 (需要转换为 Radian)
        // 负号是因为图像坐标系 y 轴向下，通常顺时针为正，我们需要逆时针矫正
        let radians = -self.angle.to_radians();

        // 使用 imageproc 的几何变换，支持自定义填充色
        // 或者简单使用 image::imageops::rotate
        // 这里演示使用 imageproc 以获得更好的填充控制
        let rotated = imageproc::geometric_transformations::rotate_about_center(
            &gray,
            radians,
            imageproc::geometric_transformations::Interpolation::Bilinear,
            Luma([self.background_color]),
        );

        Ok(DynamicImage::ImageLuma8(rotated))
    }
}

/// 辅助函数：基于霍夫变换检测倾斜角
fn detect_skew_hough(img: &GrayImage) -> f32 {
    // 1. 边缘检测 (Canny)
    let edges = imageproc::edges::canny(img, 50.0, 150.0);

    // 2. 霍夫直线变换
    let (w, h) = img.dimensions();
    // 动态计算阈值，避免小图检测不到或大图线条太多
    let threshold = (w.min(h) / 10).max(50);

    let lines = imageproc::hough::detect_lines(
        &edges,
        imageproc::hough::LineDetectionOptions {
            vote_threshold: threshold,
            suppression_radius: 10,
        },
    );

    // 3. 统计角度
    let mut angles = Vec::new();
    for line in lines {
        // 【修正2】PolarLine 使用 angle_in_degrees 字段
        // 注意：angle_in_degrees 通常是法线角度 (Normal Angle, Theta)
        // 直线角度 = 法线角度 - 90度
        // 例如：水平线的法线是垂直的(90度)，90-90=0度
        let angle_val = line.angle_in_degrees as f32;
        let mut angle_deg = angle_val - 90.0;

        // 简单归一化到 -90 ~ 90 范围 (针对某些情况 theta > 180)
        while angle_deg <= -90.0 {
            angle_deg += 180.0;
        }
        while angle_deg > 90.0 {
            angle_deg -= 180.0;
        }

        // 我们只关心水平附近的线 (文字行)
        // 假设倾斜不会超过 +/- 45 度
        if angle_deg.abs() < 45.0 {
            angles.push(angle_deg);
        }
    }

    if angles.is_empty() {
        return 0.0;
    }

    // 4. 计算中位数角度 (去噪能力比平均值强)
    // 防止浮点数排序 panic
    angles.sort_by(|a, b| a.partial_cmp(b).unwrap_or(std::cmp::Ordering::Equal));
    let mid = angles.len() / 2;
    angles[mid]
}
