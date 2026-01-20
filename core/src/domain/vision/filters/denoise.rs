use anyhow::Ok;
use image::DynamicImage;
use imageproc::filter::median_filter;
use serde::{Deserialize, Serialize};
use ts_rs::TS;

use super::ImageFilter;

/// 5. 去噪 (中值滤波)
/// radius: 窗口半径，通常 1 或 2
#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record, TS)]
pub struct DenoiseFilter {
    pub radius: u32,
}

impl ImageFilter for DenoiseFilter {
    fn apply(&self, img: &image::DynamicImage) -> anyhow::Result<image::DynamicImage> {
        let gray = img.to_luma8();
        let cleaned = median_filter(&gray, self.radius, self.radius);
        Ok(DynamicImage::ImageLuma8(cleaned))
    }
}
