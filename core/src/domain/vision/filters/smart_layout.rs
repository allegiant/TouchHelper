use super::ImageFilter;
use anyhow::Result;
use image::{DynamicImage, GrayImage, Luma};
use imageproc::region_labelling::{connected_components, Connectivity};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use ts_rs::TS;

/// 智能重排滤镜参数
#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record, TS)]
#[serde(rename_all = "camelCase")]
#[ts(rename_all = "camelCase")]
pub struct SmartLayoutFilter {
    pub padding: i32,
    pub min_width: i32,
    pub min_height: i32,
    pub fixed_height: i32, // 传 0 或 -1 代表自动
    pub align_center: bool,
}

impl ImageFilter for SmartLayoutFilter {
    fn apply(&self, img: &DynamicImage) -> Result<DynamicImage> {
        let gray = img.to_luma8();
        let (w, h) = gray.dimensions();
        let labeled = connected_components(&gray, Connectivity::Eight, Luma([0u8]));

        struct Blob {
            min_x: u32,
            max_x: u32,
            min_y: u32,
            max_y: u32,
            pixels: Vec<(u32, u32)>,
        }
        let mut blobs: HashMap<u32, Blob> = HashMap::new();

        for y in 0..h {
            for x in 0..w {
                let label = labeled.get_pixel(x, y)[0];
                if label > 0 {
                    let entry = blobs.entry(label).or_insert(Blob {
                        min_x: x,
                        max_x: x,
                        min_y: y,
                        max_y: y,
                        pixels: Vec::new(),
                    });
                    if x < entry.min_x {
                        entry.min_x = x;
                    }
                    if x > entry.max_x {
                        entry.max_x = x;
                    }
                    if y < entry.min_y {
                        entry.min_y = y;
                    }
                    if y > entry.max_y {
                        entry.max_y = y;
                    }
                    entry.pixels.push((x, y));
                }
            }
        }

        let mut valid_blobs: Vec<Blob> = blobs
            .into_iter()
            .map(|(_, blob)| blob)
            .filter(|b| {
                let w = b.max_x - b.min_x + 1;
                let h = b.max_y - b.min_y + 1;
                w >= self.min_width as u32 && h >= self.min_height as u32
            })
            .collect();

        valid_blobs.sort_by_key(|b| b.min_x);

        if valid_blobs.is_empty() {
            return Ok(img.clone());
        }

        let padding = self.padding as u32;
        let total_chars_width: u32 = valid_blobs.iter().map(|b| (b.max_x - b.min_x + 1)).sum();
        let total_padding = padding * (valid_blobs.len() as u32 + 1);
        let new_width = total_chars_width + total_padding;

        let max_blob_height = valid_blobs
            .iter()
            .map(|b| (b.max_y - b.min_y + 1))
            .max()
            .unwrap_or(0);
        let new_height = if self.fixed_height > 0 {
            self.fixed_height as u32
        } else {
            max_blob_height + padding * 2
        };

        let mut out = GrayImage::new(new_width, new_height);
        let mut current_x = padding;

        for blob in valid_blobs {
            let blob_w = blob.max_x - blob.min_x + 1;
            let blob_h = blob.max_y - blob.min_y + 1;

            let target_y = if self.align_center {
                (new_height as i32 - blob_h as i32) / 2
            } else {
                padding as i32
            };
            let target_y = target_y.max(0) as u32;

            for (src_x, src_y) in blob.pixels {
                let dest_x = current_x + (src_x - blob.min_x);
                let dest_y = target_y + (src_y - blob.min_y);
                if dest_x < new_width && dest_y < new_height {
                    out.put_pixel(dest_x, dest_y, Luma([255]));
                }
            }
            current_x += blob_w + padding;
        }

        Ok(DynamicImage::ImageLuma8(out))
    }
}
