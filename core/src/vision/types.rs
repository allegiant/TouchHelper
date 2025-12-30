// 必须引入 uniffi，或者确保 Cargo.toml 中有 uniffi = { version = "...", features = ["derive"] }
// 通常 lib.rs 中有 uniffi::setup_scaffolding!(); 就行

use serde::{Deserialize, Serialize};

// ==========================================
// 1. ColorRule (纯数据 -> Record)
// ==========================================
#[derive(Debug, Clone, uniffi::Record)] // <--- 使用 Record
pub struct ColorRule {
    pub id: i64,
    pub target_hex: String,
    pub bias_hex: String,
    pub is_enabled: bool,
}

// ==========================================
// 2. Rect (纯数据 -> Record)
// ==========================================
#[derive(Debug, Clone, Copy, uniffi::Record)] // <--- 使用 Record
pub struct Rect {
    pub left: i32,
    pub top: i32,
    pub width: u32,
    pub height: u32,
}

// ==========================================
// 3. GridParams (纯数据 -> Record)
// ==========================================
#[derive(Debug, Clone, Copy, uniffi::Record)]
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

// 1. 必须引入 uniffi，并且加上 Serialize/Deserialize 以支持 JS
#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct BinarizationFilter {
    pub threshold_min: i32,
    pub threshold_max: i32,
    pub is_rgb_avg: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct GrayscaleFilter {}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct ColorInvertFilter {}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct DenoiseFilter {
    pub radius: u32,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct BlackWhiteInvertFilter {}

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
