use std::collections::HashMap;

use image::{DynamicImage, GrayImage, Luma, Rgba, RgbaImage};
use imageproc::contrast::{adaptive_threshold, otsu_level, threshold};
use imageproc::distance_transform::Norm;
use imageproc::filter::median_filter;
use imageproc::morphology::{dilate, erode};
use imageproc::region_labelling::{connected_components, Connectivity};

use super::types::{ColorRule, GrayscaleMode, PosterizationFilter, PosterizationMode};
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

/// 3. 灰度化 (增强版)
/// 支持多种灰度算法，针对不同场景优化
pub fn grayscale(img: &DynamicImage, mode: GrayscaleMode) -> DynamicImage {
    // 如果是标准加权，直接使用 image 库的高性能实现
    if let GrayscaleMode::Weighted = mode {
        return DynamicImage::ImageLuma8(img.to_luma8());
    }

    let rgb = img.to_rgb8();
    let (w, h) = rgb.dimensions();
    let mut out = GrayImage::new(w, h);

    // 遍历像素进行处理
    for (x, y, pixel) in rgb.enumerate_pixels() {
        let r = pixel[0];
        let g = pixel[1];
        let b = pixel[2];

        let val = match mode {
            GrayscaleMode::Weighted => unreachable!(), // 上面已处理

            // 最大值法: 取 R,G,B 中最亮的。
            // 效果: 图像整体变亮，浅色背景(如纸张纹理)会趋向于纯白(255)，适合文档 OCR 预处理。
            GrayscaleMode::Max => r.max(g).max(b),

            // 最小值法: 取 R,G,B 中最暗的。
            // 效果: 图像整体变暗，适合提取亮色背景下的深色骨架。
            GrayscaleMode::Min => r.min(g).min(b),

            // 单通道提取
            // 红色通道: 红色物体(如印章)会变白(消失)，蓝黑色文字保留。
            GrayscaleMode::Red => r,

            // 绿色通道: 拜耳阵列中绿色像素最多，噪点最少，细节通常最清晰。
            GrayscaleMode::Green => g,

            // 蓝色通道: 较少单独使用，除非特定色偏场景。
            GrayscaleMode::Blue => b,
        };

        out.put_pixel(x, y, Luma([val]));
    }

    DynamicImage::ImageLuma8(out)
}

/// 4. 反色 (Invert)
pub fn invert(img: &DynamicImage) -> DynamicImage {
    let mut out = img.clone();
    image::imageops::invert(&mut out);
    out
}

pub fn posterize(img: &DynamicImage, filter: &PosterizationFilter) -> DynamicImage {
    let rgb = img.to_rgb8();
    let (w, h) = rgb.dimensions();

    // 1. 准备 LUT (如果是多值化模式)
    let lut = if filter.is_multi_value {
        let mut t = [0u8; 256];
        let levels = filter.level.max(2) as f32;
        let step = if levels > 1.0 {
            255.0 / (levels - 1.0)
        } else {
            255.0
        };
        for i in 0..=255 {
            let v = i as f32;
            let q = (v / step).round() * step;
            t[i] = q.clamp(0.0, 255.0) as u8;
        }
        Some(t)
    } else {
        None
    };

    // 2. 准备输出容器
    // 多值化输出彩色(RGBA)，通道提取输出灰度(Luma)
    // 为了代码简洁，我们这里分两个大循环写，或者用闭包

    if let Some(lut) = lut {
        // --- 模式 A: 彩色多值化 (RGB / HSV 量化) ---
        let mut out = RgbaImage::new(w, h);

        for (x, y, pixel) in rgb.enumerate_pixels() {
            let (v1, v2, v3) = match filter.mode {
                PosterizationMode::Rgb => (pixel[0], pixel[1], pixel[2]),
                PosterizationMode::Hsv => rgb_to_hsv(pixel[0], pixel[1], pixel[2]),
            };

            // 量化
            let q1 = lut[v1 as usize];
            let q2 = lut[v2 as usize];
            let q3 = lut[v3 as usize];

            // 如果是 HSV 模式，量化完还得转回 RGB 显示给用户看
            let (r, g, b) = match filter.mode {
                PosterizationMode::Rgb => (q1, q2, q3),
                PosterizationMode::Hsv => hsv_to_rgb(q1, q2, q3),
            };

            out.put_pixel(x, y, Rgba([r, g, b, 255]));
        }
        DynamicImage::ImageRgba8(out)
    } else {
        // --- 模式 B: 通道提取 ---
        let mut out = GrayImage::new(w, h);
        let c1_on = filter.channel1;
        let c2_on = filter.channel2;
        let c3_on = filter.channel3;

        for (x, y, pixel) in rgb.enumerate_pixels() {
            // 根据模式获取三个分量
            let (v1, v2, v3) = match filter.mode {
                PosterizationMode::Rgb => (pixel[0], pixel[1], pixel[2]),
                // 转换到 HSV: v1=H, v2=S, v3=V
                PosterizationMode::Hsv => rgb_to_hsv(pixel[0], pixel[1], pixel[2]),
            };

            let v1 = v1 as i16;
            let v2 = v2 as i16;
            let v3 = v3 as i16;

            let val: u8 = match (c1_on, c2_on, c3_on) {
                // 单通道
                (true, false, false) => v1 as u8,
                (false, true, false) => v2 as u8,
                (false, false, true) => v3 as u8,

                // 双通道差分 (这是最强的功能)
                // RGB模式: |R-G| 等
                // HSV模式: |H-S| (通常用来找特定饱和度的特定颜色), 或者 |S-V|
                (true, true, false) => (v1 - v2).abs() as u8,
                (true, false, true) => (v1 - v3).abs() as u8,
                (false, true, true) => (v2 - v3).abs() as u8,

                _ => ((v1 + v2 + v3) / 3) as u8,
            };
            out.put_pixel(x, y, Luma([val]));
        }
        DynamicImage::ImageLuma8(out)
    }
}

// --- 辅助算法: 极速整数版 RGB <-> HSV ---

/// 将 RGB (0-255) 转换为 HSV (0-255)
/// H: 0-255 对应 0-360度
/// S: 0-255 对应 0-100%
/// V: 0-255 对应 0-100%
#[inline]
fn rgb_to_hsv(r: u8, g: u8, b: u8) -> (u8, u8, u8) {
    let r = r as f32;
    let g = g as f32;
    let b = b as f32;

    let max = r.max(g).max(b);
    let min = r.min(g).min(b);
    let delta = max - min;

    // V (Value)
    let v = max;

    // S (Saturation)
    let s = if max == 0.0 {
        0.0
    } else {
        (delta / max) * 255.0
    };

    // H (Hue)
    let h = if delta == 0.0 {
        0.0
    } else {
        let temp = if max == r {
            (g - b) / delta + (if g < b { 6.0 } else { 0.0 })
        } else if max == g {
            (b - r) / delta + 2.0
        } else {
            (r - g) / delta + 4.0
        };
        temp * 60.0
    };

    // 将 H (0-360) 映射到 0-255
    let h_u8 = (h / 360.0 * 255.0) as u8;
    let s_u8 = s as u8;
    let v_u8 = v as u8;

    (h_u8, s_u8, v_u8)
}

/// 将 HSV (0-255) 转回 RGB (用于预览)
#[inline]
fn hsv_to_rgb(h: u8, s: u8, v: u8) -> (u8, u8, u8) {
    let h = (h as f32 / 255.0) * 360.0;
    let s = s as f32 / 255.0;
    let v = v as f32 / 255.0;

    let c = v * s;
    let x = c * (1.0 - ((h / 60.0) % 2.0 - 1.0).abs());
    let m = v - c;

    let (r1, g1, b1) = if h < 60.0 {
        (c, x, 0.0)
    } else if h < 120.0 {
        (x, c, 0.0)
    } else if h < 180.0 {
        (0.0, c, x)
    } else if h < 240.0 {
        (0.0, x, c)
    } else if h < 300.0 {
        (x, 0.0, c)
    } else {
        (c, 0.0, x)
    };

    (
        ((r1 + m) * 255.0) as u8,
        ((g1 + m) * 255.0) as u8,
        ((b1 + m) * 255.0) as u8,
    )
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

/// [新增] 智能清除杂点 (Connected Component Analysis)
pub fn remove_noise_smart(
    img: &DynamicImage,
    min_area: u32,
    gap: u32,
    remove_white: bool,
) -> DynamicImage {
    let gray = img.to_luma8();
    let (w, h) = gray.dimensions();

    // 1. 预处理：确定什么是"前景"
    // 如果去除白点，原图直接用；如果去除黑点，先反色
    let work_img = if remove_white {
        gray.clone()
    } else {
        let mut inv = gray.clone();
        image::imageops::invert(&mut inv);
        inv
    };

    // 2. 膨胀处理 (处理间隙)
    // 如果 gap > 0，先膨胀让断开的笔画连起来
    let analysis_img = if gap > 0 {
        dilate(&work_img, Norm::LInf, gap as u8)
    } else {
        work_img
    };

    // 3. 计算连通域
    // 0 是背景，>0 是连通域 ID
    let labeled = connected_components(&analysis_img, Connectivity::Eight, Luma([0u8]));

    // 4. 统计每个连通域的面积
    let mut area_map = HashMap::new();
    for p in labeled.pixels() {
        let label = p[0];
        if label > 0 {
            *area_map.entry(label).or_insert(0) += 1;
        }
    }

    // 5. 擦除杂点
    // 我们在原始 gray 图上操作
    let mut out = gray.clone();
    // 决定用什么颜色填充擦除区域 (去白点用黑填，去黑点用白填)
    let fill_color = if remove_white { Luma([0]) } else { Luma([255]) };

    for y in 0..h {
        for x in 0..w {
            // 获取当前像素在分析图中的 Label
            // 注意：要查 labeled 图，因为它是经过 gap 处理后的逻辑归属
            let label = labeled.get_pixel(x, y)[0];

            if label > 0 {
                // 如果这个像素所属的连通域面积 <= 阈值，擦掉
                if let Some(&area) = area_map.get(&label) {
                    if area <= min_area {
                        out.put_pixel(x, y, fill_color);
                    }
                }
            }
        }
    }

    DynamicImage::ImageLuma8(out)
}

/// 基于形态学的直线去除 (Remove Lines Morphological)
///
/// 原理：
/// 1. 使用一个长条形的核（例如 20x1）对图像进行“腐蚀”。只有连续长度超过 20 的横向像素才能幸存。
/// 2. 对幸存的像素进行“膨胀”，恢复其原本的粗细。此时我们就得到了只有横线的图像。
/// 3. 将原图减去这些横线，剩下的就是文字。
///
/// * `img`: 输入图像 (建议是二值化后的图像)
/// * `min_length`: 线条的最小长度 (核的大小)。长度小于此值的线条不会被去除。
/// * `remove_horizontal`: 是否去除横线
/// * `remove_vertical`: 是否去除竖线
pub fn remove_lines_morph(
    img: &DynamicImage,
    min_length: u32,
    remove_horizontal: bool,
    remove_vertical: bool,
) -> DynamicImage {
    let gray = img.to_luma8();
    // 复制一份作为画布，用于后续减法操作
    let mut result = gray.clone();

    // 辅助函数：执行开运算 (Opening) 提取特定形状
    // imageproc 的 morphology 模块目前主要支持矩形核，这里我们手动组合 erode/dilate 模拟开运算
    let perform_opening = |input: &GrayImage, width: u32, height: u32| -> GrayImage {
        // 1. 腐蚀 (Erode): 消除小于核结构的细节
        // imageproc 的 erode/dilate 默认使用 LInf 范数 (3x3 方形)，我们需要自定义长方形核的效果
        // 这里为了性能和简单，我们使用多次迭代或者自定义核逻辑。
        // 但 imageproc::morphology::erode_mut 接受一个 DistanceTransform 范数，比较受限。
        // 为了实现精确的长方形核 (MxN)，最稳健的方法是使用 stencil 库或者手动滑窗。
        // 考虑到这是 OCR 工具，我们用一个简化的逻辑：
        // 既然是去除直线，我们假设线条是标准的。

        // 暂用 imageproc 的基础操作模拟 (注意：标准 imageproc 库对自定义核支持有限，
        // 实际生产中建议引入 `imageproc::morphology::dilate/erode` 配合自定义核，
        // 或者简单地循环处理。)

        // --- 简易实现版 (针对水平/垂直优化的极速版) ---
        let mut eroded = GrayImage::new(input.width(), input.height());
        let mut opened = GrayImage::new(input.width(), input.height());

        // A. 腐蚀水平方向 (1 x width)
        if height == 1 {
            let r = width / 2; // 半径
            for y in 0..input.height() {
                for x in 0..input.width() {
                    let mut min_val = 255;
                    // 检查左右范围
                    for k in 0..width {
                        let offset = k as i32 - r as i32;
                        let nx = x as i32 + offset;
                        if nx >= 0 && nx < input.width() as i32 {
                            min_val = min_val.min(input.get_pixel(nx as u32, y)[0]);
                        } else {
                            min_val = 0; // 边界外视为0
                        }
                    }
                    eroded.put_pixel(x, y, Luma([min_val]));
                }
            }
        }
        // B. 腐蚀垂直方向 (height x 1)
        else {
            let r = height / 2;
            for x in 0..input.width() {
                for y in 0..input.height() {
                    let mut min_val = 255;
                    for k in 0..height {
                        let offset = k as i32 - r as i32;
                        let ny = y as i32 + offset;
                        if ny >= 0 && ny < input.height() as i32 {
                            min_val = min_val.min(input.get_pixel(x, ny as u32)[0]);
                        } else {
                            min_val = 0;
                        }
                    }
                    eroded.put_pixel(x, y, Luma([min_val]));
                }
            }
        }

        // 2. 膨胀 (Dilate): 恢复骨架大小 (逻辑同上，只是取 max)
        // 为节省篇幅，这里复用上面的逻辑结构，改为取 Max
        if height == 1 {
            let r = width / 2;
            for y in 0..input.height() {
                for x in 0..input.width() {
                    let mut max_val = 0;
                    for k in 0..width {
                        let offset = k as i32 - r as i32;
                        let nx = x as i32 + offset;
                        if nx >= 0 && nx < input.width() as i32 {
                            max_val = max_val.max(eroded.get_pixel(nx as u32, y)[0]);
                        }
                    }
                    opened.put_pixel(x, y, Luma([max_val]));
                }
            }
        } else {
            let r = height / 2;
            for x in 0..input.width() {
                for y in 0..input.height() {
                    let mut max_val = 0;
                    for k in 0..height {
                        let offset = k as i32 - r as i32;
                        let ny = y as i32 + offset;
                        if ny >= 0 && ny < input.height() as i32 {
                            max_val = max_val.max(eroded.get_pixel(x, ny as u32)[0]);
                        }
                    }
                    opened.put_pixel(x, y, Luma([max_val]));
                }
            }
        }

        opened
    };

    // 1. 提取并减去水平线
    if remove_horizontal {
        // 核大小：宽度=min_length, 高度=1
        let h_lines = perform_opening(&gray, min_length, 1);

        // 从结果中减去线条 (Result = Result - Lines)
        for (x, y, pixel) in result.enumerate_pixels_mut() {
            let line_val = h_lines.get_pixel(x, y)[0];
            // 假设是白字黑底 (255是内容)。如果是线条(255)，则减去。
            // 饱和减法：255 - 255 = 0
            pixel.0[0] = pixel.0[0].saturating_sub(line_val);
        }
    }

    // 2. 提取并减去垂直线
    // 注意：我们要基于已经被减去横线的图继续操作吗？
    // 通常并行提取更好，或者基于原图提取。这里基于原图提取，防止交叉点被双重扣除导致断裂。
    if remove_vertical {
        // 核大小：宽度=1, 高度=min_length
        let v_lines = perform_opening(&gray, 1, min_length);

        for (x, y, pixel) in result.enumerate_pixels_mut() {
            let line_val = v_lines.get_pixel(x, y)[0];
            pixel.0[0] = pixel.0[0].saturating_sub(line_val);
        }
    }

    DynamicImage::ImageLuma8(result)
}
