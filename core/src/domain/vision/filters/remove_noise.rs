use std::collections::HashMap;

use image::{DynamicImage, Luma};
use imageproc::distance_transform::Norm;
use imageproc::morphology::dilate;
use imageproc::region_labelling::{connected_components, Connectivity};
use serde::{Deserialize, Serialize};
use ts_rs::TS;

use super::ImageFilter;

// 清除杂点滤镜结构
#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record, TS)]
#[serde(rename_all = "camelCase")]
#[ts(rename_all = "camelCase")]
pub struct RemoveNoiseFilter {
    pub min_area: i32,      // 对应 "阈值范围" (0~8)
    pub gap: i32,           // 对应 "间隙数值"
    pub remove_white: bool, // true="白色点去除", false="黑色点去除"
}

impl ImageFilter for RemoveNoiseFilter {
    fn apply(&self, img: &image::DynamicImage) -> anyhow::Result<image::DynamicImage> {
        let gray = img.to_luma8();
        let (w, h) = gray.dimensions();

        // 1. 预处理：确定什么是"前景"
        // 如果去除白点，原图直接用；如果去除黑点，先反色
        let work_img = if self.remove_white {
            gray.clone()
        } else {
            let mut inv = gray.clone();
            image::imageops::invert(&mut inv);
            inv
        };

        // 2. 膨胀处理 (处理间隙)
        // 如果 gap > 0，先膨胀让断开的笔画连起来
        let analysis_img = if self.gap > 0 {
            dilate(&work_img, Norm::LInf, self.gap as u8)
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
        let fill_color = if self.remove_white {
            Luma([0])
        } else {
            Luma([255])
        };

        for y in 0..h {
            for x in 0..w {
                // 获取当前像素在分析图中的 Label
                // 注意：要查 labeled 图，因为它是经过 gap 处理后的逻辑归属
                let label = labeled.get_pixel(x, y)[0];

                if label > 0 {
                    // 如果这个像素所属的连通域面积 <= 阈值，擦掉
                    if let Some(&area) = area_map.get(&label) {
                        if area <= self.min_area {
                            out.put_pixel(x, y, fill_color);
                        }
                    }
                }
            }
        }

        Ok(DynamicImage::ImageLuma8(out))
    }
}
