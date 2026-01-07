use image::{DynamicImage, GrayImage, Luma, Rgba, RgbaImage};
use imageproc::contrast::{adaptive_threshold, otsu_level, threshold};
use imageproc::distance_transform::Norm;
use imageproc::filter::median_filter;
use imageproc::morphology::{dilate, erode};

use super::types::ColorRule;
use super::{colors, skeleton};

/// 1. 二值化 (固定阈值)
/// 将图片转为灰度，然后根据阈值转为纯黑白
pub fn binarize(img: &DynamicImage, threshold_val: u8) -> DynamicImage {
    let gray = img.to_luma8();
    // imageproc::contrast::threshold 会将 > threshold 的设为 255，否则 0
    let binary = threshold(
        &gray,
        threshold_val,
        imageproc::contrast::ThresholdType::Binary,
    );
    DynamicImage::ImageLuma8(binary)
}

/// 2. 二值化 (RGB 平均值范围)
/// 这是您之前 Kotlin 代码中的逻辑：计算 (R+G+B)/3，如果在 min~max 之间则为白，否则黑
pub fn binarize_rgb_avg(img: &DynamicImage, min: u8, max: u8) -> DynamicImage {
    let rgb = img.to_rgb8();
    let (w, h) = rgb.dimensions();

    // 创建一个新的灰度图缓冲区
    let mut out = GrayImage::new(w, h);

    // 遍历所有像素 (Rust 的迭代器通常比手动 for 循环快，且做了边界检查优化)
    for (x, y, pixel) in rgb.enumerate_pixels() {
        // pixel 是 Rgb([r, g, b])
        let sum: u16 = pixel[0] as u16 + pixel[1] as u16 + pixel[2] as u16;
        let avg = (sum / 3) as u8;

        if avg >= min && avg <= max {
            out.put_pixel(x, y, Luma([255]));
        } else {
            out.put_pixel(x, y, Luma([0]));
        }
    }

    DynamicImage::ImageLuma8(out)
}

/// 自动二值化 (对应截图中的 "自动 / OTSU算法")
/// 自动计算最佳阈值，适合光照均匀但亮度不定的图片
pub fn binarize_otsu(img: &DynamicImage) -> DynamicImage {
    let gray = img.to_luma8();
    let threshold_val = otsu_level(&gray);
    let binary = threshold(
        &gray,
        threshold_val,
        imageproc::contrast::ThresholdType::Binary,
    );
    DynamicImage::ImageLuma8(binary)
}

/// 智能二值化 (对应截图中的 "智能 / 点数均衡")
/// block_radius: 局部区域半径，例如 10 表示 21x21 的区域
/// block_radius = 15 => 局部窗口大小为 31x31
pub fn binarize_adaptive(img: &DynamicImage, block_radius: u32) -> DynamicImage {
    let gray = img.to_luma8();
    // adaptive_threshold 会根据局部像素平均值进行二值化
    let binary = adaptive_threshold(&gray, block_radius);
    DynamicImage::ImageLuma8(binary)
}

/// 8. Sauvola 局部自适应二值化 (进阶 OCR 专用)
/// 适合处理光照不均、有阴影的文字图片
/// window_size: 窗口大小，一般设为 15~30 左右 (必须是奇数)
/// k: 敏感度系数，一般设为 0.2 到 0.5 (值越大，背景越干净，但字可能变细)
pub fn binarize_sauvola(img: &DynamicImage, window_size: u32, k: f64) -> DynamicImage {
    let gray = img.to_luma8();
    let (w, h) = gray.dimensions();
    let mut out = GrayImage::new(w, h);

    // 强制转为奇数，防止 panic 或逻辑错误
    let safe_window = if window_size % 2 == 0 {
        window_size + 1
    } else {
        window_size
    };
    // 限制最小窗口
    let safe_window = safe_window.max(3);

    // 预计算积分图 (Integral Image) 可以极大加速局部均值计算，
    // 但为了代码简洁易懂，这里演示滑动窗口的逻辑（Rust 的 release 模式下速度尚可）
    // 如果追求极致速度，建议后续引入 `integral_image` 优化

    let r = (safe_window / 2) as i32; // 半径

    for y in 0..h {
        for x in 0..w {
            let mut sum = 0.0;
            let mut sum_sq = 0.0;
            let mut count = 0.0;

            // 遍历局部窗口 (边界检查)
            for ky in -r..=r {
                for kx in -r..=r {
                    let nx = x as i32 + kx;
                    let ny = y as i32 + ky;

                    if nx >= 0 && nx < w as i32 && ny >= 0 && ny < h as i32 {
                        let val = gray.get_pixel(nx as u32, ny as u32)[0] as f64;
                        sum += val;
                        sum_sq += val * val;
                        count += 1.0;
                    }
                }
            }

            let mean = sum / count;
            // 计算标准差 (Standard Deviation)
            let variance = (sum_sq / count) - (mean * mean);
            let std_dev = variance.sqrt();

            // Sauvola 阈值公式: T = m * [ 1 + k * (s/R - 1) ]
            // R 通常取 128 (对于 8位 灰度图)
            let threshold_val = mean * (1.0 + k * (std_dev / 128.0 - 1.0));

            let pixel_val = gray.get_pixel(x, y)[0] as f64;

            if pixel_val > threshold_val {
                out.put_pixel(x, y, Luma([255])); // 背景 (白)
            } else {
                out.put_pixel(x, y, Luma([0])); // 文字 (黑)
            }
        }
    }

    DynamicImage::ImageLuma8(out)
}

/// 3. 灰度化
pub fn grayscale(img: &DynamicImage) -> DynamicImage {
    DynamicImage::ImageLuma8(img.to_luma8())
}

/// 4. 反色 (Invert)
pub fn invert(img: &DynamicImage) -> DynamicImage {
    let mut out = img.clone();
    image::imageops::invert(&mut out);
    out
}

/// 5. 去噪 (中值滤波)
/// radius: 窗口半径，通常 1 或 2
pub fn denoise(img: &DynamicImage, radius: u32) -> DynamicImage {
    let gray = img.to_luma8();
    // imageproc::filter::median_filter
    let cleaned = median_filter(&gray, radius, radius);
    DynamicImage::ImageLuma8(cleaned)
}

/// 6. 膨胀 (Dilate) - 让白色区域变大 (连接断笔)
pub fn dilate_filter(img: &DynamicImage) -> DynamicImage {
    let gray = img.to_luma8();
    // Norm::LInf 对应于 3x3 的方形核 (8-connectivity)
    let dilated = dilate(&gray, Norm::LInf, 1);
    DynamicImage::ImageLuma8(dilated)
}

/// 7. 腐蚀 (Erode) - 让白色区域变小 (分离粘连)
pub fn erode_filter(img: &DynamicImage) -> DynamicImage {
    let gray = img.to_luma8();
    let eroded = erode(&gray, Norm::LInf, 1);
    DynamicImage::ImageLuma8(eroded)
}

pub fn skeleton(img: &DynamicImage) -> DynamicImage {
    // 1. 先转为灰度图 (这是 ImageBuffer 类型)
    let mut gray = img.to_luma8();
    skeleton::apply_skeleton(&mut gray);
    DynamicImage::ImageLuma8(gray)
}

/// 9. 缩放 (Resize)
pub fn resize(img: &DynamicImage, width: u32, height: u32) -> DynamicImage {
    // FilterType::Lanczos3 质量最好但最慢，Triangle/Nearest 较快
    img.resize_exact(width, height, image::imageops::FilterType::Lanczos3)
}

/// 多点颜色选取 (支持反色、保留原色)
pub fn keep_multi_colors(
    img: &DynamicImage,
    rules: &[ColorRule],
    is_invert: bool,
    keep_original: bool,
) -> DynamicImage {
    // 1. 预解析 Hex 颜色，避免在像素循环中重复解析字符串
    let parsed_rules: Vec<([u8; 3], [u8; 3])> = rules
        .iter()
        .filter(|r| r.is_enabled) // 只处理启用的规则
        .map(|r| {
            (
                colors::parse_hex(&r.target_hex),
                colors::parse_hex(&r.bias_hex),
            )
        })
        .collect();

    // 2. 准备
    let rgb = img.to_rgb8();
    let (w, h) = rgb.dimensions();

    // 3. 根据是否保留原色，决定输出格式
    if keep_original {
        // --- 模式 A: 保留原色 (输出彩色图) ---
        let mut out = RgbaImage::new(w, h);

        for (x, y, pixel) in rgb.enumerate_pixels() {
            let p = pixel.0;

            // 检查匹配
            let mut matched = false;
            for (target, bias) in &parsed_rules {
                if colors::is_match(p, *target, *bias) {
                    matched = true;
                    break;
                }
            }

            // 反色逻辑：invert=true 时，不匹配的才是我们要留下的
            let should_keep = if is_invert { !matched } else { matched };

            if should_keep {
                // 保留原色 (补上 Alpha=255)
                out.put_pixel(x, y, Rgba([p[0], p[1], p[2], 255]));
            } else {
                // 背景涂黑
                out.put_pixel(x, y, Rgba([0, 0, 0, 255]));
            }
        }
        DynamicImage::ImageRgba8(out)
    } else {
        // --- 模式 B: 二值化 (输出黑白图) ---
        let mut out = GrayImage::new(w, h);

        for (x, y, pixel) in rgb.enumerate_pixels() {
            let p = pixel.0;

            let mut matched = false;
            for (target, bias) in &parsed_rules {
                if colors::is_match(p, *target, *bias) {
                    matched = true;
                    break;
                }
            }

            let should_keep = if is_invert { !matched } else { matched };

            if should_keep {
                out.put_pixel(x, y, Luma([255])); // 白
            } else {
                out.put_pixel(x, y, Luma([0])); // 黑
            }
        }
        DynamicImage::ImageLuma8(out)
    }
}

// --- 辅助函数 ---

fn parse_hex(hex: &str) -> [u8; 3] {
    let hex = hex.trim_start_matches('#');
    if hex.len() == 6 {
        let r = u8::from_str_radix(&hex[0..2], 16).unwrap_or(0);
        let g = u8::from_str_radix(&hex[2..4], 16).unwrap_or(0);
        let b = u8::from_str_radix(&hex[4..6], 16).unwrap_or(0);
        [r, g, b]
    } else {
        [0, 0, 0]
    }
}

fn is_color_match(c: [u8; 3], t: [u8; 3], b: [u8; 3]) -> bool {
    let r_diff = (c[0] as i16 - t[0] as i16).abs() as u8;
    let g_diff = (c[1] as i16 - t[1] as i16).abs() as u8;
    let b_diff = (c[2] as i16 - t[2] as i16).abs() as u8;

    r_diff <= b[0] && g_diff <= b[1] && b_diff <= b[2]
}
