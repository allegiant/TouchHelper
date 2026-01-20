use anyhow::Result;
use image::{DynamicImage, ImageBuffer, Rgba};

use crate::domain::vision::types::{ImageFilterWrapper, ProcessedImage, VisionError};

// =========================================================
// 无状态图像处理接口
// =========================================================

/// 通用图像处理入口
/// 接收原始像素数据和滤镜配置，返回处理后的结果
#[uniffi::export]
pub fn process_image(
    pixels: Vec<u8>,
    width: i32,
    height: i32,
    filter: ImageFilterWrapper,
) -> Result<ProcessedImage, VisionError> {
    // 1. 加载图像 (复用辅助函数逻辑)
    let img = load_image_from_raw(pixels, width, height)?;

    // 2. 执行滤镜 (一行代码搞定调度！)
    let result_img = filter
        .apply(&img)
        .map_err(|e| VisionError::ProcessError(e.to_string()))?;

    // 3. 导出结果
    export_image(&result_img)
}

// =========================================================
// 私有辅助函数
// =========================================================

fn load_image_from_raw(
    mut pixels: Vec<u8>,
    width: i32,
    height: i32,
) -> Result<DynamicImage, VisionError> {
    let width_u32 = width as u32;
    let height_u32 = height as u32;
    let expected_len = (width_u32 * height_u32 * 4) as usize;

    if pixels.len() != expected_len {
        return Err(VisionError::LoadError(format!(
            "Pixel data mismatch: expected {} bytes, got {}",
            expected_len,
            pixels.len()
        )));
    }

    // 假设输入是 BGRA (Android Bitmap 默认格式)，需要转为 RGBA
    // 如果确定输入已经是 RGBA，可以注释掉这行
    bgra_to_rgba_in_place(&mut pixels);

    let img_buffer = ImageBuffer::<Rgba<u8>, Vec<u8>>::from_raw(width_u32, height_u32, pixels)
        .ok_or_else(|| VisionError::LoadError("Failed to create image buffer".into()))?;

    Ok(DynamicImage::ImageRgba8(img_buffer))
}

fn export_image(img: &DynamicImage) -> Result<ProcessedImage, VisionError> {
    // 这里我们直接返回原始像素数据给 Kotlin，而不是编码成 PNG
    // 这样 Kotlin 端可以直接以此创建 Bitmap，性能更高
    let rgba = img.to_rgba8();

    Ok(ProcessedImage {
        width: rgba.width() as i32,
        height: rgba.height() as i32,
        pixels: rgba.into_raw(),
    })
}

/// 极速 BGRA -> RGBA 转换 (原地修改，不分配新内存)
fn bgra_to_rgba_in_place(data: &mut [u8]) {
    for chunk in data.chunks_exact_mut(4) {
        chunk.swap(0, 2); // 交换 B 和 R
    }
}
