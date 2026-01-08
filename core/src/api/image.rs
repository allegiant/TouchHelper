use image::{DynamicImage, ImageBuffer, Rgba};

use crate::vision::types::{
    BinarizationFilter, BinarizationMode, BlackWhiteInvertFilter, ColorInvertFilter, ColorRule,
    DenoiseFilter, ExtractBlobsFilter, ExtractContoursFilter, GrayscaleFilter, MultiColorFilter,
    PosterizationFilter, Rect, RemoveLinesFilter, RemoveNoiseFilter, VisionError,
};
use crate::vision::{analysis, filters};

// =========================================================
// 1. 公共辅助函数 (Private Helper)
// =========================================================

/// 通用的图像处理包装器
/// 负责：参数校验、Raw -> DynamicImage 转换、调用处理逻辑、DynamicImage -> Raw 转换
fn process_image_wrapper<F>(
    pixels: Vec<u8>,
    width: i32,
    height: i32,
    processor: F,
) -> Result<Vec<u8>, VisionError>
where
    // F 是一个闭包：接收 &DynamicImage，返回处理后的 DynamicImage
    F: FnOnce(&DynamicImage) -> DynamicImage,
{
    let width_u32 = width as u32;
    let height_u32 = height as u32;
    // RGBA 格式，每个像素 4 字节
    let expected_len = (width_u32 * height_u32 * 4) as usize;

    // 1. 校验数据长度
    if pixels.len() != expected_len {
        return Err(VisionError::LoadError(format!(
            "Pixel data mismatch: expected {} bytes, got {}",
            expected_len,
            pixels.len()
        )));
    }

    // 2. 零拷贝加载 (from_raw 直接拿走 pixels 的所有权，不产生额外内存分配)
    let img_buffer = ImageBuffer::<Rgba<u8>, Vec<u8>>::from_raw(width_u32, height_u32, pixels)
        .ok_or_else(|| VisionError::LoadError("Failed to create image buffer".to_string()))?;

    let img = DynamicImage::ImageRgba8(img_buffer);

    // 3. 执行传入的具体处理逻辑
    let processed_img = processor(&img);

    // 4. 转回 Raw Vec<u8>
    Ok(processed_img.to_rgba8().into_raw())
}

/// 对图片应用二值化滤镜
#[uniffi::export]
pub fn apply_binarization(
    pixels: Vec<u8>,
    width: i32,
    height: i32,
    filter: BinarizationFilter,
) -> Result<Vec<u8>, VisionError> {
    log::info!("Executing Binarization: {:?}", filter);

    let threshold_min_u8 = filter.threshold_min.clamp(0, 255) as u8;
    let threshold_max_u8 = filter.threshold_max.clamp(0, 255) as u8;

    // Sauvola 窗口必须是正奇数，做个安全处理
    let win_size = if filter.window_size < 3 {
        3
    } else {
        filter.window_size as u32
    };
    // 确保是奇数 (如果是偶数就+1)
    let win_size = if win_size % 2 == 0 {
        win_size + 1
    } else {
        win_size
    };

    process_image_wrapper(pixels, width, height, |img| match filter.mode {
        BinarizationMode::Otsu => filters::binarize_otsu(img),
        BinarizationMode::Adaptive => filters::binarize_sauvola(img, win_size, filter.sauvola_k),
        BinarizationMode::Manual => {
            if filter.is_rgb_avg {
                filters::binarize_rgb_avg(img, threshold_min_u8, threshold_max_u8)
            } else {
                filters::binarize(img, threshold_min_u8)
            }
        }
    })
}

#[uniffi::export]
pub fn apply_posterization_filter(
    pixels: Vec<u8>,
    width: i32,
    height: i32,
    filter: PosterizationFilter,
) -> Result<Vec<u8>, VisionError> {
    log::info!("Executing Posterization: {:?}", filter);

    process_image_wrapper(pixels, width, height, |img| {
        filters::posterize(img, &filter)
    })
}

/// 应用多点找色滤镜
#[uniffi::export]
pub fn apply_multi_color_filter(
    pixels: Vec<u8>,
    width: i32,
    height: i32,
    filter: MultiColorFilter,
) -> Result<Vec<u8>, VisionError> {
    log::info!(
        "Executing MultiColor Filter: rules={}, invert={}, original={}",
        filter.rules.len(),
        filter.is_invert,
        filter.keep_original
    );

    process_image_wrapper(pixels, width, height, |img| {
        filters::keep_multi_colors(img, &filter.rules, filter.is_invert, filter.keep_original)
    })
}

/// 对图片应用灰度滤镜
#[uniffi::export]
pub fn apply_grayscale(
    pixels: Vec<u8>,
    width: i32,
    height: i32,
    filter: GrayscaleFilter,
) -> Result<Vec<u8>, VisionError> {
    log::info!("Executing Grayscale: {:?}", filter);

    process_image_wrapper(pixels, width, height, |img| {
        // [修改] 将 filter.mode 传递给底层逻辑
        filters::grayscale(img, filter.mode)
    })
}

/// [新增] 对图片应用智能清除杂点
#[uniffi::export]
pub fn apply_remove_noise(
    pixels: Vec<u8>,
    width: i32,
    height: i32,
    filter: RemoveNoiseFilter,
) -> Result<Vec<u8>, VisionError> {
    log::info!("Executing Remove Noise Smart: {:?}", filter);

    process_image_wrapper(pixels, width, height, |img| {
        filters::remove_noise_smart(
            img,
            filter.min_area as u32,
            filter.gap as u32,
            filter.remove_white,
        )
    })
}

/// 应用形态学去直线滤镜
#[uniffi::export]
pub fn apply_remove_lines(
    pixels: Vec<u8>,
    width: i32,
    height: i32,
    filter: RemoveLinesFilter,
) -> Result<Vec<u8>, VisionError> {
    log::info!("Executing Remove Lines Morph: {:?}", filter);

    process_image_wrapper(pixels, width, height, |img| {
        filters::remove_lines_morph(
            img,
            filter.min_length as u32,
            filter.remove_horizontal,
            filter.remove_vertical,
        )
    })
}

/// 提取轮廓滤镜
#[uniffi::export]
pub fn apply_extract_contours(
    pixels: Vec<u8>,
    width: i32,
    height: i32,
    filter: ExtractContoursFilter,
) -> Result<Vec<u8>, VisionError> {
    log::info!("Executing Extract Contours: {:?}", filter);

    process_image_wrapper(pixels, width, height, |img| {
        filters::extract_contours(
            img,
            filter.is_canny,
            filter.canny_low,
            filter.canny_high,
            filter.morph_kernel as u8,
        )
    })
}

/// 提取色块
#[uniffi::export]
pub fn apply_extract_blobs(
    pixels: Vec<u8>,
    width: i32,
    height: i32,
    filter: ExtractBlobsFilter,
) -> Result<Vec<u8>, VisionError> {
    log::info!("Executing Extract Blobs: {:?}", filter);

    process_image_wrapper(pixels, width, height, |img| {
        filters::extract_blobs(
            img,
            filter.min_w,
            filter.max_w,
            filter.min_h,
            filter.max_h,
            filter.min_area,
            filter.max_area,
        )
    })
}

/// 对黑白图片应用去噪滤镜
#[uniffi::export]
pub fn apply_denoise(
    pixels: Vec<u8>,
    width: i32,
    height: i32,
    filter: DenoiseFilter,
) -> Result<Vec<u8>, VisionError> {
    log::info!("Executing Denoise with radius: {:?}", filter);

    process_image_wrapper(pixels, width, height, |img| {
        filters::denoise(img, filter.radius)
    })
}

/// 对图片应用反色滤镜
#[uniffi::export]
pub fn apply_color_invert(
    pixels: Vec<u8>,
    width: i32,
    height: i32,
    filter: ColorInvertFilter,
) -> Result<Vec<u8>, VisionError> {
    log::info!("Executing color invert: {:?}", filter);

    process_image_wrapper(pixels, width, height, |img| filters::invert(img))
}

/// 对黑白图片应用反色滤镜
#[uniffi::export]
pub fn apply_blackwhite_invert(
    pixels: Vec<u8>,
    width: i32,
    height: i32,
    filter: BlackWhiteInvertFilter,
) -> Result<Vec<u8>, VisionError> {
    log::info!("Executing black-white invert: {:?}", filter);

    process_image_wrapper(pixels, width, height, |img| filters::invert(img))
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
