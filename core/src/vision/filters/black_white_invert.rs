use anyhow::Ok;
use serde::{Deserialize, Serialize};

use super::ImageFilter;

/// 智能反色模式枚举 (对应 Kotlin 端的定义)
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, uniffi::Enum)]
pub enum InvertMode {
    AutoToWhiteBg, // 智能：确保白底黑字 (边缘是黑则反色)
    AutoToBlackBg, // 智能：确保黑底白字 (边缘是白则反色)
    Force,         // 强制：直接反色
}

/// 智能反色实现
/// 使用“边缘检测法”判定背景色，比全局统计更准确
#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct BlackWhiteInvertFilter {
    pub mode: InvertMode,
}

impl ImageFilter for BlackWhiteInvertFilter {
    fn apply(&self, img: &image::DynamicImage) -> anyhow::Result<image::DynamicImage> {
        // 1. 如果是强制模式，直接反色
        if self.mode == InvertMode::Force {
            let mut out = img.clone();
            image::imageops::invert(&mut out);
            return Ok(out);
        }

        let gray = img.to_luma8();
        let (w, h) = gray.dimensions();

        // 2. 采样边缘像素来判定背景是否为黑色
        // 阈值设为 128 (二值图通常是 0 或 255)
        let mut black_count = 0;
        let mut total_count = 0;

        let is_dark = |p: image::Luma<u8>| p[0] < 128;

        // 采样四条边
        if w > 0 && h > 0 {
            // 上下边
            for x in 0..w {
                if is_dark(*gray.get_pixel(x, 0)) {
                    black_count += 1;
                }
                if is_dark(*gray.get_pixel(x, h - 1)) {
                    black_count += 1;
                }
                total_count += 2;
            }
            // 左右边 (去掉角点避免重复)
            if h > 2 {
                for y in 1..h - 1 {
                    if is_dark(*gray.get_pixel(0, y)) {
                        black_count += 1;
                    }
                    if is_dark(*gray.get_pixel(w - 1, y)) {
                        black_count += 1;
                    }
                    total_count += 2;
                }
            }
        }

        let is_black_bg = if total_count > 0 {
            (black_count as f32 / total_count as f32) > 0.5
        } else {
            false // 默认为亮色背景
        };

        // 3. 根据目标模式决定是否反色
        let should_invert = match self.mode {
            InvertMode::AutoToWhiteBg => is_black_bg, // 如果是黑底，要反转成白底
            InvertMode::AutoToBlackBg => !is_black_bg, // 如果是白底，要反转成黑底
            InvertMode::Force => true,
        };

        if should_invert {
            let mut out = img.clone();
            image::imageops::invert(&mut out);
            Ok(out)
        } else {
            Ok(img.clone())
        }
    }
}
