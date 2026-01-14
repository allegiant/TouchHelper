use crate::vision::types::*;
use crate::vision::{colors, filters};
use image::{DynamicImage, ImageBuffer, Rgba};
use std::sync::Mutex;

/// 有状态的图像处理会话
/// 避免了每次操作都在 Kotlin 和 Rust 之间搬运像素数据
#[derive(uniffi::Object)]
pub struct ImageSession {
    // 使用 Mutex 保证线程安全 (UniFFI 对象要求 Sync)
    image: Mutex<DynamicImage>,
}

#[uniffi::export]
impl ImageSession {
    /// 创建新会话
    /// 接收 Kotlin 的 BGRA 数据 (Little Endian ARGB)
    #[uniffi::constructor]
    pub fn new(mut pixels: Vec<u8>, width: i32, height: i32) -> Result<Self, VisionError> {
        let width_u32 = width as u32;
        let height_u32 = height as u32;
        let expected_len = (width_u32 * height_u32 * 4) as usize;

        if pixels.len() != expected_len {
            return Err(VisionError::LoadError("Pixel data mismatch".into()));
        }

        // 1. 极速格式转换 BGRA -> RGBA (同 perform_segmentation 优化)
        bgra_to_rgba_in_place(&mut pixels);

        // 2. 加载图片
        let img_buffer = ImageBuffer::<Rgba<u8>, Vec<u8>>::from_raw(width_u32, height_u32, pixels)
            .ok_or_else(|| VisionError::LoadError("Failed to create image buffer".into()))?;

        Ok(Self {
            image: Mutex::new(DynamicImage::ImageRgba8(img_buffer)),
        })
    }

    /// 应用滤镜 (原地修改)
    pub fn apply_filter(&self, wrapper: ImageFilterWrapper) -> Result<(), VisionError> {
        let mut guard = self
            .image
            .lock()
            .map_err(|_| VisionError::ProcessError("Lock failed".into()))?;

        // 取出当前图片，处理后覆盖回去
        // 注意：这里我们clone了引用或者直接传参，取决于filters模块的签名。
        // 由于filters模块目前是 Fn(&DynamicImage) -> DynamicImage (返回新图)，
        // 我们需要把新图赋值给 *guard。

        let current_img = &*guard;

        let new_img = match wrapper {
            ImageFilterWrapper::Binarization(f) => {
                // 复用之前的逻辑
                let win_size = if f.window_size < 3 {
                    3
                } else {
                    f.window_size as u32
                };
                let win_size = if win_size % 2 == 0 {
                    win_size + 1
                } else {
                    win_size
                };
                match f.mode {
                    BinarizationMode::Otsu => filters::binarize_otsu(current_img),
                    BinarizationMode::Adaptive => {
                        filters::binarize_sauvola(current_img, win_size, f.sauvola_k)
                    }
                    BinarizationMode::Manual => {
                        if f.is_rgb_avg {
                            filters::binarize_rgb_avg(
                                current_img,
                                f.threshold_min as u8,
                                f.threshold_max as u8,
                            )
                        } else {
                            filters::binarize(current_img, f.threshold_min as u8)
                        }
                    }
                }
            }
            ImageFilterWrapper::Grayscale(f) => filters::grayscale(current_img, f.mode),
            ImageFilterWrapper::Posterization(f) => filters::posterize(current_img, &f),
            ImageFilterWrapper::MultiColor(f) => {
                filters::keep_multi_colors(current_img, &f.rules, f.is_invert, f.keep_original)
            }
            ImageFilterWrapper::RemoveNoise(f) => filters::remove_noise_smart(
                current_img,
                f.min_area as u32,
                f.gap as u32,
                f.remove_white,
            ),
            ImageFilterWrapper::RemoveLines(f) => filters::remove_lines_morph(
                current_img,
                f.min_length as u32,
                f.remove_horizontal,
                f.remove_vertical,
            ),
            ImageFilterWrapper::ExtractContours(f) => filters::extract_contours(
                current_img,
                f.is_canny,
                f.canny_low,
                f.canny_high,
                f.morph_kernel,
            ),
            ImageFilterWrapper::ExtractBlobs(f) => filters::extract_blobs(
                current_img,
                f.min_w,
                f.max_w,
                f.min_h,
                f.max_h,
                f.min_area,
                f.max_area,
                f.use_eight_connectivity,
            ),
            ImageFilterWrapper::Deskew(f) => {
                filters::deskew(current_img, f.angle, f.auto, f.background_color)
            }
            ImageFilterWrapper::Rotation(f) => filters::rotate_and_deskew(
                current_img,
                f.is_auto,
                f.manual_angle,
                f.max_search_angle,
                f.precision,
            ),
            ImageFilterWrapper::BlackWhiteInvert(f) => {
                let mode = match f.mode {
                    0 => InvertMode::AutoToWhiteBg,
                    1 => InvertMode::AutoToBlackBg,
                    _ => InvertMode::Force,
                };
                filters::smart_invert(current_img, mode)
            }
            ImageFilterWrapper::Morphology(f) => filters::apply_morphology(
                current_img,
                f.mode,
                f.kernel_size as u32,
                f.iterations as u32,
            ),
            ImageFilterWrapper::Denoise(f) => filters::denoise(current_img, f.radius),

            // 改变尺寸的滤镜
            ImageFilterWrapper::SmartLayout(f) => {
                let fixed_h = if f.fixed_height > 0 {
                    Some(f.fixed_height as u32)
                } else {
                    None
                };
                filters::smart_layout(
                    current_img,
                    f.padding.max(0) as u32,
                    f.min_width.max(0) as u32,
                    f.min_height.max(0) as u32,
                    fixed_h,
                    f.align_center,
                )
            }
            ImageFilterWrapper::AutoCrop(f) => {
                let target_color = if f.mode == AutoCropMode::FixedColor {
                    let [r, g, b] = colors::parse_hex(&f.fixed_color_hex);
                    Some(image::Rgb([r, g, b]))
                } else {
                    None
                };
                filters::auto_crop_smart(
                    current_img,
                    target_color,
                    f.tolerance as u8,
                    f.padding.max(0) as u32,
                    2,
                    f.noise_threshold.max(0) as u32,
                )
            }
            ImageFilterWrapper::ResizeScale(f) => {
                filters::resize_by_scale(current_img, f.scale_factor, f.high_quality)
            }
            ImageFilterWrapper::ExtendCrop(f) => filters::crop_by_points(
                current_img,
                f.x1.max(0) as u32,
                f.y1.max(0) as u32,
                f.x2.max(0) as u32,
                f.y2.max(0) as u32,
            ),
        };

        // 更新状态
        *guard = new_img;
        Ok(())
    }

    /// 导出结果
    pub fn get_image(&self) -> Result<ProcessedImage, VisionError> {
        let guard = self
            .image
            .lock()
            .map_err(|_| VisionError::ProcessError("Lock failed".into()))?;
        Ok(ProcessedImage {
            width: guard.width() as i32,
            height: guard.height() as i32,
            pixels: guard.to_rgba8().into_raw(),
        })
    }
}

// 辅助函数 (Copy from analysis_api.rs, or move to a common util)
fn bgra_to_rgba_in_place(data: &mut [u8]) {
    for chunk in data.chunks_exact_mut(4) {
        chunk.swap(0, 2);
    }
}
