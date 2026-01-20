use anyhow::Ok;
use image::{DynamicImage, GrayImage, Luma};
use imageproc::distance_transform::Norm;
use imageproc::morphology::{dilate, erode};
use serde::{Deserialize, Serialize};
use ts_rs::TS;

use super::ImageFilter;

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record, TS)]
pub struct ExtractContoursFilter {
    pub is_canny: bool,
    // Canny 参数
    pub canny_low: f32,
    pub canny_high: f32,
    // 形态学参数
    pub morph_kernel: u8,
}

impl ImageFilter for ExtractContoursFilter {
    fn apply(&self, img: &image::DynamicImage) -> anyhow::Result<image::DynamicImage> {
        let gray = img.to_luma8();

        if self.is_canny {
            // --- 模式 A: Canny 边缘检测 ---
            // imageproc 的 canny 返回的是 ImageBuffer<Luma<u8>, Vec<u8>>
            let edges = imageproc::edges::canny(&gray, self.canny_low, self.canny_high);
            Ok(DynamicImage::ImageLuma8(edges))
        } else {
            // --- 模式 B: 形态学梯度 (Gradient = Dilate - Erode) ---
            // 1. 膨胀
            let dilated = dilate(&gray, Norm::LInf, self.morph_kernel);
            // 2. 腐蚀
            let eroded = erode(&gray, Norm::LInf, self.morph_kernel);

            // 3. 相减 (Dilated - Eroded)
            let (w, h) = gray.dimensions();
            let mut out = GrayImage::new(w, h);

            for y in 0..h {
                for x in 0..w {
                    let d_val = dilated.get_pixel(x, y)[0];
                    let e_val = eroded.get_pixel(x, y)[0];
                    // 饱和相减
                    out.put_pixel(x, y, Luma([d_val.saturating_sub(e_val)]));
                }
            }
            Ok(DynamicImage::ImageLuma8(out))
        }
    }
}
