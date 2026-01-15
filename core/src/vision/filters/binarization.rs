use super::ImageFilter;
use anyhow::Result;
use image::{DynamicImage, GrayImage, Luma};
use imageproc::contrast::{otsu_level, threshold};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, uniffi::Enum)]
pub enum BinarizationMode {
    Manual,
    Adaptive,
    Otsu,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct BinarizationFilter {
    pub mode: BinarizationMode,
    pub threshold_min: i32,
    pub threshold_max: i32,
    pub is_rgb_avg: bool,
    pub sauvola_k: f64,
    pub window_size: i32,
}

impl ImageFilter for BinarizationFilter {
    fn apply(&self, img: &DynamicImage) -> Result<DynamicImage> {
        match self.mode {
            BinarizationMode::Manual => {
                if self.is_rgb_avg {
                    binarize_rgb_avg(img, self.threshold_min as u8, self.threshold_max as u8)
                } else {
                    binarize_fixed(img, self.threshold_min as u8)
                }
            }
            BinarizationMode::Adaptive => {
                // 这里的 Adaptive 映射到 Sauvola 算法
                binarize_sauvola(img, self.window_size as u32, self.sauvola_k)
            }
            BinarizationMode::Otsu => binarize_otsu(img),
        }
    }
}

// --- 原有逻辑的私有函数 (Helper Functions) ---

fn binarize_fixed(img: &DynamicImage, threshold_val: u8) -> Result<DynamicImage> {
    let gray = img.to_luma8();
    let binary = threshold(
        &gray,
        threshold_val,
        imageproc::contrast::ThresholdType::Binary,
    );
    Ok(DynamicImage::ImageLuma8(binary))
}

fn binarize_otsu(img: &DynamicImage) -> Result<DynamicImage> {
    let gray = img.to_luma8();
    let threshold_val = otsu_level(&gray);
    let binary = threshold(
        &gray,
        threshold_val,
        imageproc::contrast::ThresholdType::Binary,
    );
    Ok(DynamicImage::ImageLuma8(binary))
}

fn binarize_rgb_avg(img: &DynamicImage, min: u8, max: u8) -> Result<DynamicImage> {
    let rgb = img.to_rgb8();
    let (w, h) = rgb.dimensions();
    let mut out = GrayImage::new(w, h);

    for (x, y, pixel) in rgb.enumerate_pixels() {
        let sum: u16 = pixel[0] as u16 + pixel[1] as u16 + pixel[2] as u16;
        let avg = (sum / 3) as u8;
        if avg >= min && avg <= max {
            out.put_pixel(x, y, Luma([255]));
        } else {
            out.put_pixel(x, y, Luma([0]));
        }
    }
    Ok(DynamicImage::ImageLuma8(out))
}

fn binarize_sauvola(img: &DynamicImage, window_size: u32, k: f64) -> Result<DynamicImage> {
    let gray = img.to_luma8();
    let (w, h) = gray.dimensions();
    let mut out = GrayImage::new(w, h);

    let safe_window = (if window_size % 2 == 0 {
        window_size + 1
    } else {
        window_size
    })
    .max(3);
    let r = (safe_window / 2) as i32;

    for y in 0..h {
        for x in 0..w {
            let mut sum = 0.0;
            let mut sum_sq = 0.0;
            let mut count = 0.0;

            for ky in -r..=r {
                for kx in -r..=r {
                    let nx = x as i32 + kx;
                    let ny = y as i32 + ky;
                    if nx >= 0 && nx < w as i32 && ny >= 0 && ny < h as i32 {
                        let val = gray.get_pixel(nx as u32, ny as u32)[0] as f64;
                        sum += val;
                        sum_sq += val * val;
                        count += 1.0;
                    }
                }
            }

            let mean = sum / count;
            let variance = (sum_sq / count) - (mean * mean);
            let std_dev = variance.sqrt();
            let threshold_val = mean * (1.0 + k * (std_dev / 128.0 - 1.0));

            let pixel_val = gray.get_pixel(x, y)[0] as f64;
            if pixel_val > threshold_val {
                out.put_pixel(x, y, Luma([255]));
            } else {
                out.put_pixel(x, y, Luma([0]));
            }
        }
    }
    Ok(DynamicImage::ImageLuma8(out))
}
