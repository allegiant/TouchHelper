// 必须引入 uniffi，或者确保 Cargo.toml 中有 uniffi = { version = "...", features = ["derive"] }
// 通常 lib.rs 中有 uniffi::setup_scaffolding!(); 就行

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

// 对应 Kotlin 的 ColorFilterType
#[derive(Debug, Clone, Copy, uniffi::Enum)]
pub enum ColorFilterType {
    Binarization,
    ColorPick,
    Posterize,
    Grayscale,
}

// 对应 Kotlin 的 BlackWhiteFilterType
#[derive(Debug, Clone, Copy, uniffi::Enum)]
pub enum BlackWhiteFilterType {
    Denoise,
    RemoveLines,
    Contours,
    ExtractBlobs,
    Deskew,
    RotateCorrect,
    Invert,
    DilateErode,
    Skeleton,
    FenceAdjust,
    ValidImage,
    KeepSize,
}

// 对应 Kotlin 的 CommonFilterType
#[derive(Debug, Clone, Copy, uniffi::Enum)]
pub enum CommonFilterType {
    ScaleRatio,
    ScaleNorm,
    FixedRotate,
    ExtendCrop,
    FixedSmooth,
    MedianBlur,
}

// 为了在统一接口中使用，我们可以定义一个包含所有滤镜的大枚举
// 注意：UniFFI 目前对嵌套枚举的支持有限，通常建议在接口参数中直接使用具体的枚举，
// 或者定义一个扁平化的 ImageFilter 枚举。
#[derive(Debug, Clone, Copy, uniffi::Enum)]
pub enum ImageFilter {
    // Color filters
    Color(ColorFilterType),
    // Black & White filters
    BlackWhite(BlackWhiteFilterType),
    // Common filters
    Common(CommonFilterType),
    // 或者，如果你只是想有一个通用的 "ViewFilter" (浏览模式)
    View,
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
