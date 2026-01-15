use anyhow::Result;
use image::DynamicImage;

/// 所有滤镜必须实现的统一接口
/// 使用 anyhow::Result 处理潜在的图像处理错误
pub trait ImageFilter {
    fn apply(&self, img: &DynamicImage) -> Result<DynamicImage>;
}

// 导出各个子模块 (根据需要创建文件)
pub mod auto_crop;
pub mod binarization;
pub mod black_white_invert;
pub mod denoise;
pub mod deskew;
pub mod extend_crop;
pub mod extract_blobs;
pub mod extract_contours;
pub mod grayscale;
pub mod morphology;
pub mod multi_color;
pub mod posterization;
pub mod remove_lines;
pub mod remove_noise;
pub mod resize_scale;
pub mod rotation;
pub mod smart_layout;

// 方便外部直接 use
pub use auto_crop::*;
pub use binarization::*;
pub use black_white_invert::*;
pub use denoise::*;
pub use deskew::*;
pub use extend_crop::*;
pub use extract_blobs::*;
pub use extract_contours::*;
pub use grayscale::*;
pub use morphology::*;
pub use multi_color::*;
pub use posterization::*;
pub use remove_lines::*;
pub use remove_noise::*;
pub use resize_scale::*;
pub use rotation::*;
pub use smart_layout::*;
