use super::ImageFilter;
use anyhow::Result;
use image::{DynamicImage, GrayImage, Luma};
use serde::{Deserialize, Serialize};
use ts_rs::TS;

// [新增] 定义灰度模式枚举
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, uniffi::Enum, TS)]
#[serde(rename_all = "camelCase")]
#[ts(rename_all = "camelCase")]
pub enum GrayscaleMode {
    Weighted, // 标准加权平均 (默认)
    Max,      // 最大值法 (去色/高亮) - 适合白底黑字 OCR
    Min,      // 最小值法 - 适合黑底白字
    Red,      // 红色通道 - 过滤红色印章
    Green,    // 绿色通道 - 细节最丰富，噪点少
    Blue,     // 蓝色通道
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record, TS)]
#[serde(rename_all = "camelCase")]
#[ts(rename_all = "camelCase")]
pub struct GrayscaleFilter {
    pub mode: GrayscaleMode,
}

impl ImageFilter for GrayscaleFilter {
    fn apply(&self, img: &DynamicImage) -> Result<DynamicImage> {
        if let GrayscaleMode::Weighted = self.mode {
            return Ok(DynamicImage::ImageLuma8(img.to_luma8()));
        }

        let rgb = img.to_rgb8();
        let (w, h) = rgb.dimensions();
        let mut out = GrayImage::new(w, h);

        for (x, y, pixel) in rgb.enumerate_pixels() {
            let r = pixel[0];
            let g = pixel[1];
            let b = pixel[2];

            let val = match self.mode {
                GrayscaleMode::Weighted => unreachable!(),
                GrayscaleMode::Max => r.max(g).max(b),
                GrayscaleMode::Min => r.min(g).min(b),
                GrayscaleMode::Red => r,
                GrayscaleMode::Green => g,
                GrayscaleMode::Blue => b,
            };
            out.put_pixel(x, y, Luma([val]));
        }
        Ok(DynamicImage::ImageLuma8(out))
    }
}
