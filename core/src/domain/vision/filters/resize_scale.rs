use image::GenericImageView;
use serde::{Deserialize, Serialize};
use ts_rs::TS;

use super::ImageFilter;

///// [新增] 按倍率缩放 (Resize by Scale)
#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record, TS)]
pub struct ResizeScaleFilter {
    pub scale_factor: f32,  // 缩放倍率 (例如 0.5, 2.0)
    pub high_quality: bool, // true=Lanczos3(平滑), false=Nearest(硬边)
}

impl ImageFilter for ResizeScaleFilter {
    fn apply(&self, img: &image::DynamicImage) -> anyhow::Result<image::DynamicImage> {
        let (width, height) = img.dimensions();

        // 1. 安全检查：如果缩放比例无效或是 1.0，直接返回原图
        if self.scale_factor <= 0.0 || (self.scale_factor - 1.0).abs() < f32::EPSILON {
            return Ok(img.clone());
        }

        // 2. 计算新尺寸 (使用 round 四舍五入，避免 100*1.5=149.999 变成 149)
        let new_width = (width as f32 * self.scale_factor).round() as u32;
        let new_height = (height as f32 * self.scale_factor).round() as u32;

        // 防止无效尺寸
        if new_width == 0 || new_height == 0 {
            return Ok(img.clone());
        }

        // 3. 选择算法
        // Lanczos3: 质量最好，适合照片/截图
        // Nearest: 速度最快，适合像素风或二值化后的图(保持硬边缘)
        let filter = if self.high_quality {
            image::imageops::FilterType::Lanczos3
        } else {
            image::imageops::FilterType::Nearest
        };

        // 4. 执行缩放 (强制使用计算出的尺寸)
        Ok(img.resize_exact(new_width, new_height, filter))
    }
}
