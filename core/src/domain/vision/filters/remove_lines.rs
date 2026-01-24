use anyhow::Ok;
use image::{DynamicImage, GrayImage, Luma};
use serde::{Deserialize, Serialize};
use ts_rs::TS;

use super::ImageFilter;

/// 去除直线滤镜结构
#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record, TS)]
#[serde(rename_all = "camelCase")]
#[ts(rename_all = "camelCase")]
pub struct RemoveLinesFilter {
    pub min_length: i32, // 线条的最小长度 (核的大小)。长度小于此值的线条不会被去除
    pub remove_horizontal: bool, // 是否去除横线
    pub remove_vertical: bool, // 是否去除竖线
}

impl ImageFilter for RemoveLinesFilter {
    fn apply(&self, img: &image::DynamicImage) -> anyhow::Result<image::DynamicImage> {
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
        if self.remove_horizontal {
            // 核大小：宽度=min_length, 高度=1
            let h_lines = perform_opening(&gray, self.min_length as u32, 1);

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
        if self.remove_vertical {
            // 核大小：宽度=1, 高度=min_length
            let v_lines = perform_opening(&gray, 1, self.min_length as u32);

            for (x, y, pixel) in result.enumerate_pixels_mut() {
                let line_val = v_lines.get_pixel(x, y)[0];
                pixel.0[0] = pixel.0[0].saturating_sub(line_val);
            }
        }
        Ok(DynamicImage::ImageLuma8(result))
    }
}
