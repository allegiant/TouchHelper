// 必须引入 uniffi，或者确保 Cargo.toml 中有 uniffi = { version = "...", features = ["derive"] }
// 通常 lib.rs 中有 uniffi::setup_scaffolding!(); 就行

use anyhow::Result;
use image::DynamicImage;
use serde::{Deserialize, Serialize};
use ts_rs::TS;

use super::filters::{
    AutoCropFilter, BinarizationFilter, BlackWhiteInvertFilter, DenoiseFilter, DeskewFilter,
    ExtendCropFilter, ExtractBlobsFilter, ExtractContoursFilter, GrayscaleFilter, ImageFilter,
    MorphologyFilter, MultiColorFilter, PosterizationFilter, RemoveLinesFilter, RemoveNoiseFilter,
    ResizeScaleFilter, RotationFilter, SmartLayoutFilter,
};

// 统一滤镜包装器 (Union Wrapper)
/// 这允许我们在单次调用中传递任意类型的滤镜
#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Enum, TS)]
pub enum ImageFilterWrapper {
    Binarization(BinarizationFilter),
    Posterization(PosterizationFilter),
    MultiColor(MultiColorFilter),
    Grayscale(GrayscaleFilter),
    RemoveNoise(RemoveNoiseFilter),
    RemoveLines(RemoveLinesFilter),
    ExtractContours(ExtractContoursFilter),
    ExtractBlobs(ExtractBlobsFilter),
    Deskew(DeskewFilter),
    Rotation(RotationFilter),
    BlackWhiteInvert(BlackWhiteInvertFilter),
    Morphology(MorphologyFilter),
    SmartLayout(SmartLayoutFilter),
    AutoCrop(AutoCropFilter),
    ResizeScale(ResizeScaleFilter),
    ExtendCrop(ExtendCropFilter),
    Denoise(DenoiseFilter),
}

impl ImageFilterWrapper {
    pub fn apply(&self, img: &DynamicImage) -> Result<DynamicImage> {
        match self {
            // 将每个枚举变体映射到对应的 .apply() 方法
            ImageFilterWrapper::Binarization(f) => f.apply(img),
            ImageFilterWrapper::Posterization(f) => f.apply(img),
            ImageFilterWrapper::MultiColor(f) => f.apply(img),
            ImageFilterWrapper::Grayscale(f) => f.apply(img),
            ImageFilterWrapper::RemoveNoise(f) => f.apply(img),
            ImageFilterWrapper::RemoveLines(f) => f.apply(img),
            ImageFilterWrapper::ExtractContours(f) => f.apply(img),
            ImageFilterWrapper::ExtractBlobs(f) => f.apply(img),
            ImageFilterWrapper::Deskew(f) => f.apply(img),
            ImageFilterWrapper::Rotation(f) => f.apply(img),
            ImageFilterWrapper::BlackWhiteInvert(f) => f.apply(img),
            ImageFilterWrapper::Morphology(f) => f.apply(img),
            ImageFilterWrapper::SmartLayout(f) => f.apply(img),
            ImageFilterWrapper::AutoCrop(f) => f.apply(img),
            ImageFilterWrapper::ResizeScale(f) => f.apply(img),
            ImageFilterWrapper::ExtendCrop(f) => f.apply(img),
            ImageFilterWrapper::Denoise(f) => f.apply(img),
        }
    }
}

// ==========================================
// 1. ColorRule (纯数据 -> Record)
// ==========================================
//
#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record, TS)]
pub struct ColorRule {
    pub id: i64,
    pub target_hex: String,
    pub bias_hex: String,
    pub is_enabled: bool,
}

// ==========================================
// 2. Rect (纯数据 -> Record)
// ==========================================
#[derive(Debug, Clone, Copy, uniffi::Record, TS)] // <--- 使用 Record
pub struct Rect {
    pub left: i32,
    pub top: i32,
    pub width: u32,
    pub height: u32,
}

// ==========================================
// 3. GridParams (纯数据 -> Record)
// ==========================================
#[derive(Debug, Clone, Copy, uniffi::Record, TS)]
pub struct GridParams {
    pub x: i32,
    pub y: i32,
    pub w: i32,
    pub h: i32,
    pub col_gap: i32,
    pub row_gap: i32,
    pub col_count: i32,
    pub row_count: i32,
}

// 1. 新增：处理后的图像结果（包含新尺寸）
#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record, TS)]
pub struct ProcessedImage {
    pub width: i32,
    pub height: i32,
    pub pixels: Vec<u8>,
}

// 【新增】定义错误类型
#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum VisionError {
    #[error("Failed to load image: {0}")]
    LoadError(String),

    #[error("Image processing failed: {0}")]
    ProcessError(String),

    #[error("Encoding failed: {0}")]
    EncodeError(String),
}

// [新增] 切割模式枚举
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, uniffi::Enum, TS)]
pub enum SegmentationMode {
    FixedGrid,     // 固定网格
    Projection,    // 投影切割 (XY轴扫描)
    ConnectedComp, // 智能连通域 (Blob)
}

// 统一的切割参数配置
#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record, TS)]
pub struct SegmentationConfig {
    pub mode: SegmentationMode,

    // --- 通用参数 ---
    pub padding: i32, // 结果微调留白

    // [逻辑变更] 这里的 min/max 现在作用于 "最终目标"，而不是 "中间连通块"
    pub min_width: u32,
    pub min_height: u32,

    // [新增] 最大尺寸限制 (0 表示不限制)
    pub max_width: u32,
    pub max_height: u32,

    // [新增] 合并距离 (0 表示不开启合并)
    // 如果两个框的距离小于此值，它们会被合并为一个大框
    pub merge_distance: u32,

    // --- 模式A: FixedGrid 参数 ---
    pub start_x: i32,
    pub start_y: i32,
    pub cell_width: u32,
    pub cell_height: u32,
    pub col_count: u32,
    pub row_count: u32,
    pub col_gap: i32,
    pub row_gap: i32,

    // --- 模式B: Projection 参数 ---
    pub split_rows: bool,
    pub split_cols: bool,
    pub projection_threshold: u8,
}
