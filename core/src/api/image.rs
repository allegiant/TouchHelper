use crate::vision::types::{
    BlackWhiteFilterType, ColorFilterType, ColorRule, ImageFilter, Rect, VisionError,
};
use crate::vision::{analysis, filters};

/// 应用滤镜
/// ⚡️ 性能优化版：接收 Raw RGBA Pixels
#[uniffi::export]
pub fn apply_filter(
    pixels: Vec<u8>,
    width: i32,
    height: i32,
    filter: ImageFilter,
    param1: Option<i32>,
    param2: Option<i32>,
    param3: Option<i32>,
) -> Result<Vec<u8>, VisionError> {
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

    // 极速加载
    let img_buffer =
        image::ImageBuffer::<image::Rgba<u8>, Vec<u8>>::from_raw(width_u32, height_u32, pixels)
            .ok_or_else(|| VisionError::LoadError("Failed to create image buffer".to_string()))?;

    let img = image::DynamicImage::ImageRgba8(img_buffer);

    // 处理图像
    let processed_img = match filter {
        ImageFilter::Color(cf) => match cf {
            ColorFilterType::Binarization => {
                let min = param1.unwrap_or(0) as u8;
                let max = param2.unwrap_or(255) as u8;
                // param3: 1 = 使用 RGB 平均值 (默认), 0 = 使用单阈值 (min 作为阈值)
                let use_rgb_avg = param3.unwrap_or(1) == 1;

                if use_rgb_avg {
                    // 逻辑 1: RGB 均值必须在 min~max 之间
                    filters::binarize_rgb_avg(&img, min, max)
                } else {
                    // 逻辑 2: 标准二值化，亮度 > min 变白
                    filters::binarize(&img, min)
                }
            }
            ColorFilterType::Grayscale => filters::grayscale(&img),
            ColorFilterType::Invert => filters::invert(&img),
            _ => img,
        },
        ImageFilter::BlackWhite(bw) => match bw {
            BlackWhiteFilterType::Denoise => filters::denoise(&img, 1),
            BlackWhiteFilterType::Invert => filters::invert(&img),
            _ => img,
        },
        _ => img,
    };

    Ok(processed_img.to_rgba8().into_raw())
}

/// 扫描组件
#[uniffi::export]
pub fn scan_components(
    pixels: Vec<u8>,
    width: i32,
    height: i32,
    rules: Vec<ColorRule>,
    is_grid_mode: bool,
    grid_rows: Option<i32>,
    grid_cols: Option<i32>,
) -> Result<Vec<Rect>, VisionError> {
    let width_u32 = width as u32;
    let height_u32 = height as u32;
    let expected_len = (width_u32 * height_u32 * 4) as usize;

    if pixels.len() != expected_len {
        return Err(VisionError::LoadError("Pixel data mismatch".to_string()));
    }

    let img_buffer =
        image::ImageBuffer::<image::Rgba<u8>, Vec<u8>>::from_raw(width_u32, height_u32, pixels)
            .ok_or_else(|| VisionError::LoadError("Failed to create image buffer".to_string()))?;
    let img = image::DynamicImage::ImageRgba8(img_buffer);

    if is_grid_mode {
        // 【实现网格切分逻辑】
        // 使用 grid_rows 和 grid_cols 计算切分矩形
        let rows = grid_rows.unwrap_or(1).max(1) as u32;
        let cols = grid_cols.unwrap_or(1).max(1) as u32;

        let cell_w = width_u32 / cols;
        let cell_h = height_u32 / rows;

        let mut rects = Vec::new();

        for r in 0..rows {
            for c in 0..cols {
                let left = c * cell_w;
                let top = r * cell_h;
                // 最后一个格子可能需要补齐余数，这里简单处理为固定大小
                rects.push(Rect {
                    left: left as i32,
                    top: top as i32,
                    width: cell_w,
                    height: cell_h,
                });
            }
        }
        Ok(rects)
    } else {
        // 智能连通区域识别
        // 传入最小宽高 (1, 1) 防止过滤掉有效像素
        let rects = analysis::scan_connected_components(&img, rules, 1, 1);
        Ok(rects)
    }
}

