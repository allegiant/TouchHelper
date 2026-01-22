use crate::domain::vision::analysis::compute_binary_feature; // 引用底层算法
use crate::domain::vision::types::VisionError;
use anyhow::Result;
use image::{DynamicImage, ImageBuffer, Rgba};

/// 专门暴露给 Kotlin "FontMaker" 使用的工具函数
/// 放在这里是因为它是 "Scripting Support" 的一部分，而不是 Domain 的一部分
#[uniffi::export]
pub fn font_extract_feature(
    pixels: Vec<u8>,
    width: i32,
    height: i32,
) -> Result<String, VisionError> {
    let width_u32 = width as u32;
    let height_u32 = height as u32;

    // 1. 数据适配 (FFI -> Rust Domain)
    let img_buffer = ImageBuffer::<Rgba<u8>, Vec<u8>>::from_raw(width_u32, height_u32, pixels)
        .ok_or_else(|| VisionError::LoadError("Invalid pixel data".into()))?;

    // 2. 调用底层核心算法
    let feature = compute_binary_feature(&DynamicImage::ImageRgba8(img_buffer));

    // 3. 返回结果
    Ok(feature)
}
