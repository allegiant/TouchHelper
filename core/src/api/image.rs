use image::{DynamicImage, ImageBuffer, Rgba};

use crate::vision::types::{
    AutoCropFilter, AutoCropMode, BinarizationFilter, BinarizationMode, BlackWhiteInvertFilter,
    DenoiseFilter, DeskewFilter, ExtractBlobsFilter, ExtractContoursFilter, GrayscaleFilter,
    InvertMode, MorphologyFilter, MultiColorFilter, PosterizationFilter, ProcessedImage,
    RemoveLinesFilter, RemoveNoiseFilter, ResizeScaleFilter, RotationFilter, SmartLayoutFilter,
    VisionError,
};
use crate::vision::{colors, filters};

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
            filter.use_eight_connectivity, // [新增传递] 假设您已在 Kotlin/UDL 中添加了此字段
        )
    })
}

/// 倾斜校正滤镜
#[uniffi::export]
pub fn apply_deskew(
    pixels: Vec<u8>,
    width: i32,
    height: i32,
    filter: DeskewFilter,
) -> Result<Vec<u8>, VisionError> {
    log::info!("Executing Deskew: {:?}", filter);

    process_image_wrapper(pixels, width, height, |img| {
        filters::deskew(img, filter.angle, filter.auto, filter.background_color)
    })
}
/// 旋转矫正
#[uniffi::export]
pub fn apply_rotate(
    pixels: Vec<u8>,
    width: i32,
    height: i32,
    filter: RotationFilter,
) -> Result<Vec<u8>, VisionError> {
    log::info!("Executing ratate: {:?}", filter);
    process_image_wrapper(pixels, width, height, |img| {
        filters::rotate_and_deskew(
            img,
            filter.is_auto,
            filter.manual_angle,
            filter.max_search_angle,
            filter.precision,
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

/// 对黑白图片应用反色滤镜
#[uniffi::export]
pub fn apply_blackwhite_invert(
    pixels: Vec<u8>,
    width: i32,
    height: i32,
    filter: BlackWhiteInvertFilter,
) -> Result<Vec<u8>, VisionError> {
    log::info!("Executing black-white invert: {:?}", filter);

    // 将 Kotlin 传来的 Int/Enum 映射为 Rust 枚举
    // 假设 filter.mode 是 i32 或 enum: 0=AutoWhite, 1=AutoBlack, 2=Force
    let mode = match filter.mode {
        0 => InvertMode::AutoToWhiteBg,
        1 => InvertMode::AutoToBlackBg,
        _ => InvertMode::Force,
    };

    process_image_wrapper(pixels, width, height, |img| {
        filters::smart_invert(img, mode)
    })
}

/// [新增] 应用形态学滤镜
#[uniffi::export]
pub fn apply_morphology_filter(
    pixels: Vec<u8>,
    width: i32,
    height: i32,
    filter: MorphologyFilter,
) -> Result<Vec<u8>, VisionError> {
    log::info!("Executing Morphology: {:?}", filter);

    process_image_wrapper(pixels, width, height, |img| {
        filters::apply_morphology(
            img,
            filter.mode,
            filter.kernel_size as u32,
            filter.iterations as u32,
        )
    })
}

#[uniffi::export]
pub fn apply_smart_layout(
    pixels: Vec<u8>,
    width: i32,
    height: i32,
    filter: SmartLayoutFilter,
) -> Result<ProcessedImage, VisionError> {
    log::info!("Executing Smart Layout: {:?}", filter);

    // 1. 手动构建 DynamicImage (模仿 process_image_wrapper 的前半部分)
    let width_u32 = width as u32;
    let height_u32 = height as u32;
    let expected_len = (width_u32 * height_u32 * 4) as usize;

    if pixels.len() != expected_len {
        return Err(VisionError::LoadError("Pixel data mismatch".into()));
    }

    let img_buffer = ImageBuffer::<Rgba<u8>, Vec<u8>>::from_raw(width_u32, height_u32, pixels)
        .ok_or_else(|| VisionError::LoadError("Failed to create image buffer".into()))?;
    let img = DynamicImage::ImageRgba8(img_buffer);

    // 2. 调用核心算法
    let fixed_h = if filter.fixed_height > 0 {
        Some(filter.fixed_height as u32)
    } else {
        None
    };

    let result_img = filters::smart_layout(
        &img,
        filter.padding.max(0) as u32,
        filter.min_width.max(0) as u32,
        filter.min_height.max(0) as u32,
        fixed_h,
        filter.align_center,
    );

    // 3. 返回包含新尺寸的结果
    Ok(ProcessedImage {
        width: result_img.width() as i32,
        height: result_img.height() as i32,
        pixels: result_img.to_rgba8().into_raw(),
    })
}

// core/src/api/image.rs

/// [新增] 应用自动裁剪
/// 返回 ProcessedImage 因为图片尺寸会发生变化
#[uniffi::export]
pub fn apply_auto_crop(
    pixels: Vec<u8>,
    width: i32,
    height: i32,
    filter: AutoCropFilter,
) -> Result<ProcessedImage, VisionError> {
    log::info!("Executing Auto Crop: {:?}", filter);

    // 1. 构建 ImageBuffer (标准流程)
    let width_u32 = width as u32;
    let height_u32 = height as u32;
    let expected_len = (width_u32 * height_u32 * 4) as usize;

    if pixels.len() != expected_len {
        return Err(VisionError::LoadError("Pixel data mismatch".into()));
    }

    let img_buffer = ImageBuffer::<Rgba<u8>, Vec<u8>>::from_raw(width_u32, height_u32, pixels)
        .ok_or_else(|| VisionError::LoadError("Failed to create image buffer".into()))?;
    let img = DynamicImage::ImageRgba8(img_buffer);

    // 2. 处理固定颜色模式
    let target_color = if filter.mode == AutoCropMode::FixedColor {
        // 解析 Hex 字符串 (#RRGGBB)
        let [r, g, b] = colors::parse_hex(&filter.fixed_color_hex);
        Some(image::Rgb([r, g, b]))
    } else {
        None
    };

    // 3. 调用核心算法
    let result_img = filters::auto_crop_smart(
        &img,
        target_color,
        filter.tolerance as u8,
        filter.padding.max(0) as u32,
        2, // 步长 skip_step 固定为 2 以提升性能
        filter.noise_threshold.max(0) as u32,
    );

    // 4. 返回结果
    Ok(ProcessedImage {
        width: result_img.width() as i32,
        height: result_img.height() as i32,
        pixels: result_img.to_rgba8().into_raw(),
    })
}

/// [新增] 应用按倍率缩放
/// 返回 ProcessedImage 因为图片尺寸会发生变化
#[uniffi::export]
pub fn apply_resize_scale(
    pixels: Vec<u8>,
    width: i32,
    height: i32,
    filter: ResizeScaleFilter,
) -> Result<ProcessedImage, VisionError> {
    log::info!("Executing Resize Scale: {:?}", filter);

    // 1. 标准图片加载流程
    let width_u32 = width as u32;
    let height_u32 = height as u32;
    let expected_len = (width_u32 * height_u32 * 4) as usize;

    if pixels.len() != expected_len {
        return Err(VisionError::LoadError("Pixel data mismatch".into()));
    }

    let img_buffer = ImageBuffer::<Rgba<u8>, Vec<u8>>::from_raw(width_u32, height_u32, pixels)
        .ok_or_else(|| VisionError::LoadError("Failed to create image buffer".into()))?;
    let img = DynamicImage::ImageRgba8(img_buffer);

    // 2. 调用核心算法
    let result_img = filters::resize_by_scale(&img, filter.scale_factor, filter.high_quality);

    // 3. 返回新尺寸和新数据
    Ok(ProcessedImage {
        width: result_img.width() as i32,
        height: result_img.height() as i32,
        pixels: result_img.to_rgba8().into_raw(),
    })
}
