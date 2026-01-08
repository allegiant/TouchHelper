// 必须引入 uniffi，或者确保 Cargo.toml 中有 uniffi = { version = "...", features = ["derive"] }
// 通常 lib.rs 中有 uniffi::setup_scaffolding!(); 就行

use serde::{Deserialize, Serialize};

// [新增] 模式枚举
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, uniffi::Enum)]
pub enum PosterizationMode {
    Rgb,
    Hsv,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct PosterizationFilter {
    pub mode: PosterizationMode,
    pub is_multi_value: bool,
    pub level: i32,
    pub channel1: bool,
    pub channel2: bool,
    pub channel3: bool,
}

// ==========================================
// 4. MultiColorFilter(颜色选取)
// ==========================================
#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct MultiColorFilter {
    // 颜色规则列表
    pub rules: Vec<ColorRule>,
    // 背景色/反色模式：勾选后，匹配到的颜色会被剔除，未匹配的保留
    pub is_invert: bool,
    // 颜色选留：勾选后保留原色，不勾选则二值化(白)
    pub keep_original: bool,
}

// ==========================================
// 1. ColorRule (纯数据 -> Record)
// ==========================================
//
#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
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

// 1. 新增枚举：对应 Kotlin 里的 BinarizationMode
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, uniffi::Enum)]
pub enum BinarizationMode {
    Manual,   // 手动 (RGB平均 或 固定阈值)
    Adaptive, // 智能 (Sauvola / 局部自适应)
    Otsu,     // 自动 (大津法)
}

// 1. 必须引入 uniffi，并且加上 Serialize/Deserialize 以支持 JS
#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct BinarizationFilter {
    // 【新增】模式选择
    pub mode: BinarizationMode,
    pub threshold_min: i32,
    pub threshold_max: i32,
    pub is_rgb_avg: bool,
    pub sauvola_k: f64,   //Sauvola 敏感度 K
    pub window_size: i32, //Sauvola 窗口大小
}

// [新增] 定义灰度模式枚举
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, uniffi::Enum)]
pub enum GrayscaleMode {
    Weighted, // 标准加权平均 (默认)
    Max,      // 最大值法 (去色/高亮) - 适合白底黑字 OCR
    Min,      // 最小值法 - 适合黑底白字
    Red,      // 红色通道 - 过滤红色印章
    Green,    // 绿色通道 - 细节最丰富，噪点少
    Blue,     // 蓝色通道
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct GrayscaleFilter {
    pub mode: GrayscaleMode,
}

// 清除杂点滤镜结构
#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct RemoveNoiseFilter {
    pub min_area: i32,      // 对应 "阈值范围" (0~8)
    pub gap: i32,           // 对应 "间隙数值"
    pub remove_white: bool, // true="白色点去除", false="黑色点去除"
}
/// 去除直线滤镜结构
#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct RemoveLinesFilter {
    pub min_length: i32, // 线条的最小长度 (核的大小)。长度小于此值的线条不会被去除
    pub remove_horizontal: bool, // 是否去除横线
    pub remove_vertical: bool, // 是否去除竖线
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct ExtractContoursFilter {
    pub is_canny: bool,
    // Canny 参数
    pub canny_low: f32,
    pub canny_high: f32,
    // 形态学参数
    pub morph_kernel: u8,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct ExtractBlobsFilter {
    pub min_w: u32,
    pub max_w: u32,
    pub min_h: u32,
    pub max_h: u32,
    pub min_area: u32,
    pub max_area: u32,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct DeskewFilter {
    pub angle: f32, // 输入的角度 (Degrees)
    pub auto: bool,
    pub background_color: u8,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct RotationFilter {
    pub is_auto: bool,
    pub manual_angle: f64,     // 手动模式角度
    pub max_search_angle: f64, // 自动模式：最大搜索范围 (如 30.0)
    pub precision: f64,        // 自动模式：精度步长 (如 0.5)
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct DenoiseFilter {
    pub radius: u32,
}

/// 智能反色模式枚举 (对应 Kotlin 端的定义)
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, uniffi::Enum)]
pub enum InvertMode {
    AutoToWhiteBg, // 智能：确保白底黑字 (边缘是黑则反色)
    AutoToBlackBg, // 智能：确保黑底白字 (边缘是白则反色)
    Force,         // 强制：直接反色
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct BlackWhiteInvertFilter {
    pub mode: i32,
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
