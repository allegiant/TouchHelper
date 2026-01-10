// core/src/api/analysis.rs

use crate::vision::analysis;
use crate::vision::types::{Rect, SegmentationConfig, VisionError};
use image::{DynamicImage, ImageBuffer, Rgba}; // 调用内部纯算法模块

/// 暴露给 Kotlin 的接口
/// 统一使用 Result<..., VisionError> 进行错误处理
/// 统一接收 pixels: Vec<u8> 作为输入
#[uniffi::export]
pub fn perform_segmentation(
    pixels: Vec<u8>,
    width: i32,
    height: i32,
    config: SegmentationConfig,
) -> Result<Vec<Rect>, VisionError> {
    // 1. 数据校验与加载 (逻辑与 image.rs 中的 scan_components 保持一致)
    let width_u32 = width as u32;
    let height_u32 = height as u32;
    let expected_len = (width_u32 * height_u32 * 4) as usize; // RGBA 4通道

    if pixels.len() != expected_len {
        return Err(VisionError::LoadError(format!(
            "Pixel data mismatch: expected {} bytes, got {}",
            expected_len,
            pixels.len()
        )));
    }

    // 2. 零拷贝构建 ImageBuffer
    let img_buffer = ImageBuffer::<Rgba<u8>, Vec<u8>>::from_raw(width_u32, height_u32, pixels)
        .ok_or_else(|| VisionError::LoadError("Failed to create image buffer".to_string()))?;

    let img = DynamicImage::ImageRgba8(img_buffer);

    // 3. 调用内部纯算法 (internal vision logic)
    // 内部算法只负责计算，不负责处理加载错误，所以它返回 Vec<Rect> 是合理的
    let rects = analysis::perform_segmentation(&img, &config);

    // 4. 返回结果
    Ok(rects)
}

