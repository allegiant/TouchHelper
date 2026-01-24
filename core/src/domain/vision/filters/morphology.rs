use anyhow::Ok;
use image::{DynamicImage, GrayImage, Luma};
use imageproc::distance_transform::Norm;
use imageproc::morphology::{dilate, erode};
use serde::{Deserialize, Serialize};
use ts_rs::TS;

use super::ImageFilter;

// [新增] 形态学操作模式
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, uniffi::Enum, TS)]
#[serde(rename_all = "camelCase")]
#[ts(rename_all = "camelCase")]
pub enum MorphologyMode {
    Dilate,   // 膨胀 (扩张白色)
    Erode,    // 腐蚀 (收缩白色)
    Open,     // 开运算 (先腐蚀后膨胀 -> 去噪)
    Close,    // 闭运算 (先膨胀后腐蚀 -> 连笔)
    Gradient, // 形态学梯度 (膨胀 - 腐蚀 -> 轮廓)
}

/// [新增] 高级形态学变换
/// radius: 核半径 (1 => 3x3, 2 => 5x5)
/// iterations: 执行次数
#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record, TS)]
#[serde(rename_all = "camelCase")]
#[ts(rename_all = "camelCase")]
pub struct MorphologyFilter {
    pub mode: MorphologyMode,
    pub kernel_size: i32, // 核大小 (半径)，实际大小 = 2*r + 1
    pub iterations: i32,  // 迭代次数
}

impl ImageFilter for MorphologyFilter {
    fn apply(&self, img: &image::DynamicImage) -> anyhow::Result<image::DynamicImage> {
        let gray = img.to_luma8();

        // 辅助函数：执行腐蚀
        let do_erode = |input: &GrayImage, r: u32, iter: u32| -> GrayImage {
            // Norm::LInf 代表切比雪夫距离，对应方形核 (Square Kernel)
            // 這是最适合像素文字处理的形状
            let mut temp = input.clone();
            for _ in 0..iter {
                temp = erode(&temp, Norm::LInf, r as u8);
            }
            temp
        };

        // 辅助函数：执行膨胀
        let do_dilate = |input: &GrayImage, r: u32, iter: u32| -> GrayImage {
            let mut temp = input.clone();
            for _ in 0..iter {
                temp = dilate(&temp, Norm::LInf, r as u8);
            }
            temp
        };

        let out = match self.mode {
            MorphologyMode::Dilate => {
                do_dilate(&gray, self.kernel_size as u32, self.iterations as u32)
            }
            MorphologyMode::Erode => {
                do_erode(&gray, self.kernel_size as u32, self.iterations as u32)
            }

            // 开运算：先腐蚀，后膨胀 (去除孤立噪点)
            MorphologyMode::Open => {
                let temp = do_erode(&gray, self.kernel_size as u32, self.iterations as u32);
                do_dilate(&temp, self.kernel_size as u32, self.iterations as u32)
            }

            // 闭运算：先膨胀，后腐蚀 (连接断裂笔画)
            MorphologyMode::Close => {
                let temp = do_dilate(&gray, self.kernel_size as u32, self.iterations as u32);
                do_erode(&temp, self.kernel_size as u32, self.iterations as u32)
            }

            // 形态学梯度：膨胀图 - 腐蚀图 (提取空心轮廓)
            MorphologyMode::Gradient => {
                let dilated = do_dilate(&gray, self.kernel_size as u32, self.iterations as u32);
                let eroded = do_erode(&gray, self.kernel_size as u32, self.iterations as u32);

                let (w, h) = gray.dimensions();
                let mut diff = GrayImage::new(w, h);
                for y in 0..h {
                    for x in 0..w {
                        let d = dilated.get_pixel(x, y)[0];
                        let e = eroded.get_pixel(x, y)[0];
                        diff.put_pixel(x, y, Luma([d.saturating_sub(e)]));
                    }
                }
                diff
            }
        };
        Ok(DynamicImage::ImageLuma8(out))
    }
}
