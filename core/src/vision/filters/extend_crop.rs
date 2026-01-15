use image::{DynamicImage, GenericImageView};
use serde::{Deserialize, Serialize};

use super::ImageFilter;

// 延伸裁剪滤镜参数
#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct ExtendCropFilter {
    pub x1: i32,
    pub y1: i32,
    pub x2: i32,
    pub y2: i32,
}

impl ImageFilter for ExtendCropFilter {
    fn apply(&self, img: &image::DynamicImage) -> anyhow::Result<image::DynamicImage> {
        let (w, h) = img.dimensions();

        // 1. 坐标转换与边界限制 (i32 -> u32)
        // 使用 clamp 确保坐标在图片范围内，防止 panic
        let p1_x = self.x1.clamp(0, w as i32) as u32;
        let p1_y = self.y1.clamp(0, h as i32) as u32;
        let p2_x = self.x2.clamp(0, w as i32) as u32;
        let p2_y = self.y2.clamp(0, h as i32) as u32;

        // 1. 自动计算 min/max，允许用户先点右下再点左上
        let x_min = p1_x.min(p2_x).clamp(0, w);
        let y_min = p1_y.min(p2_y).clamp(0, h);
        let x_max = p1_x.max(p2_x).clamp(0, w);
        let y_max = p1_y.max(p2_y).clamp(0, h);

        // 2. 计算宽高
        let crop_w = x_max - x_min;
        let crop_h = y_max - y_min;

        // 3. 避免裁剪出 0x0 的图片导致报错
        if crop_w == 0 || crop_h == 0 {
            return Ok(img.clone());
        }

        // 4. 执行裁剪
        let cropped = image::imageops::crop_imm(img, x_min, y_min, crop_w, crop_h).to_image();
        Ok(DynamicImage::ImageRgba8(cropped))
    }
}
