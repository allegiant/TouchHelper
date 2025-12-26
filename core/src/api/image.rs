use std::io::Cursor;

use image::ImageFormat;

use crate::vision::types::{
    BlackWhiteFilterType, ColorFilterType, ColorRule, ImageFilter, Rect, VisionError,
};
use crate::vision::{analysis, filters};

#[uniffi::export]
pub fn apply_filter(
    image_data: Vec<u8>,
    filter: ImageFilter,
    // 一些滤镜可能需要额外的参数，如阈值、范围等
    // 可以定义一个可选的配置结构体，或者简单的参数
    param1: Option<i32>,
) -> Result<Vec<u8>, VisionError> {
    // 1. 加载图片
    let img =
        image::load_from_memory(&image_data).map_err(|e| VisionError::LoadError(e.to_string()))?;

    let processed_img = match filter {
        ImageFilter::Color(cf) => match cf {
            ColorFilterType::Binarization => filters::binarize(&img, param1.unwrap_or(128) as u8),
            ColorFilterType::Grayscale => filters::grayscale(&img),
            // ... 其他彩色滤镜
            _ => img, // 暂未实现
        },
        ImageFilter::BlackWhite(bw) => match bw {
            BlackWhiteFilterType::Denoise => filters::denoise(&img, 1),
            BlackWhiteFilterType::Invert => filters::invert(&img),
            // ... 其他黑白滤镜
            _ => img,
        },
        ImageFilter::Common(cf) => match cf {
            // ... 通用滤镜
            _ => img,
        },
        ImageFilter::View => img, // 浏览模式，不做处理
    };

    // 3. 编码回字节数组
    let mut result_data = Vec::new();
    processed_img
        .write_to(&mut Cursor::new(&mut result_data), ImageFormat::Png)
        .map_err(|e| VisionError::EncodeError(e.to_string()))?;

    Ok(result_data)
}
#[uniffi::export]
pub fn scan_components(
    image_data: Vec<u8>,
    rules: Vec<ColorRule>,
) -> Result<Vec<Rect>, VisionError> {
    // 使用之前定义的 VisionError

    let img =
        image::load_from_memory(&image_data).map_err(|e| VisionError::LoadError(e.to_string()))?;

    // 调用分析算法，最小宽高设为 2 (可作为参数传入)
    let rects = analysis::scan_connected_components(&img, rules, 2, 2);

    Ok(rects)
}
