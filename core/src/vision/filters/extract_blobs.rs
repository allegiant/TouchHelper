use std::collections::HashMap;

use image::{DynamicImage, GrayImage, Luma};
use imageproc::region_labelling::{connected_components, Connectivity};
use serde::{Deserialize, Serialize};

use super::ImageFilter;

/// 12. 高级连通域筛选 (Extract Blobs / Filter Blobs)
/// 这是“提取色块”的现代实现版。
///
/// 原理：
/// 1. 分析图像中的连通域 (Blob)。
/// 2. 计算每个 Blob 的属性：宽度、高度、面积 (像素数)。
/// 3. 根据传入的范围 (min~max) 进行筛选。
/// 4. 只保留符合条件的 Blob，其余擦除（变黑）。
#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct ExtractBlobsFilter {
    pub min_w: u32,
    pub max_w: u32,
    pub min_h: u32,
    pub max_h: u32,
    pub min_area: u32,
    pub max_area: u32,
    pub use_eight_connectivity: bool,
}

impl ImageFilter for ExtractBlobsFilter {
    fn apply(&self, img: &image::DynamicImage) -> anyhow::Result<image::DynamicImage> {
        let gray = img.to_luma8();
        let (w, h) = gray.dimensions();

        // 1. 根据参数选择连通性
        let connectivity = if self.use_eight_connectivity {
            Connectivity::Eight
        } else {
            Connectivity::Four
        };

        // 1. 连通域标记 (0是背景，1..N 是连通域ID)
        // Connectivity::Eight 对应 8邻域 (对角线连通也算)
        let labeled = connected_components(&gray, connectivity, Luma([0u8]));

        // 2. 统计每个 Blob 的属性
        // 使用 HashMap 存储: Label ID -> (min_x, max_x, min_y, max_y, count)
        let mut stats: HashMap<u32, (u32, u32, u32, u32, u32)> = HashMap::new();

        for y in 0..h {
            for x in 0..w {
                let label = labeled.get_pixel(x, y)[0];
                if label > 0 {
                    let entry = stats.entry(label).or_insert((x, x, y, y, 0));

                    // 更新边界
                    if x < entry.0 {
                        entry.0 = x;
                    }
                    if x > entry.1 {
                        entry.1 = x;
                    }
                    if y < entry.2 {
                        entry.2 = y;
                    }
                    if y > entry.3 {
                        entry.3 = y;
                    }

                    // 更新面积
                    entry.4 += 1;
                }
            }
        }

        // 3. 确定哪些 Label 需要保留
        let mut valid_labels = HashMap::new();
        for (label, (min_x, max_x, min_y, max_y, area)) in stats {
            let blob_w = max_x - min_x + 1;
            let blob_h = max_y - min_y + 1;

            // 核心筛选逻辑
            let match_w = blob_w >= self.min_w && blob_w <= self.max_w;
            let match_h = blob_h >= self.min_h && blob_h <= self.max_h;
            let match_area = area >= self.min_area && area <= self.max_area;

            if match_w && match_h && match_area {
                valid_labels.insert(label, true);
            }
        }

        // 4. 重绘图像
        let mut out = GrayImage::new(w, h);
        for y in 0..h {
            for x in 0..w {
                let label = labeled.get_pixel(x, y)[0];
                // 如果该像素属于“有效Label”，则保留原色(或置白)，否则置黑
                if label > 0 && valid_labels.contains_key(&label) {
                    // 这里我们简单地置为 255 (白)，还原二值图
                    // 如果需要保留原图灰度，可以从 `gray` 取值
                    out.put_pixel(x, y, Luma([255]));
                } else {
                    out.put_pixel(x, y, Luma([0]));
                }
            }
        }

        Ok(DynamicImage::ImageLuma8(out))
    }
}
