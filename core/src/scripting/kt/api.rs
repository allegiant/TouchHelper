use crate::domain::vision::analysis::{compute_binary_feature, find_best_match}; // 引用底层算法
use crate::domain::vision::types::{RsFontItem, RsRecognitionResult, VisionError};
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

// ==========================================
// 单字识别接口
// ==========================================

#[uniffi::export]
pub fn rs_font_recognize(
    pixels: Vec<u8>,
    width: i32,
    height: i32,
    library: Vec<RsFontItem>,
    min_confidence: f32,
) -> Result<RsRecognitionResult, VisionError> {
    let width_u32 = width as u32;
    let height_u32 = height as u32;

    // 1. 构建图片对象
    let img_buffer = ImageBuffer::<Rgba<u8>, Vec<u8>>::from_raw(width_u32, height_u32, pixels)
        .ok_or_else(|| VisionError::LoadError("Invalid pixel data".into()))?;
    let dynamic_img = DynamicImage::ImageRgba8(img_buffer);

    // 2. 转换字库格式 (Uniffi Record -> Tuple 供底层 analysis 使用)
    // 这里进行一次转换，为了让底层 analysis 保持纯净 (不依赖 Uniffi 类型)
    let internal_lib: Vec<(String, String, u32, u32)> = library
        .iter()
        .map(|item| {
            (
                item.char_name.clone(),
                item.binary_data.clone(),
                item.width as u32,
                item.height as u32,
            )
        })
        .collect();

    // 3. 调用核心匹配算法
    let result = find_best_match(&dynamic_img, &internal_lib, min_confidence);

    match result {
        Some((name, score)) => Ok(RsRecognitionResult {
            char_name: name,
            confidence: score,
        }),
        None => Ok(RsRecognitionResult {
            char_name: "".into(),
            confidence: 0.0,
        }),
    }
}
