use image::{DynamicImage, ImageBuffer, Rgba};
use log::info;
use std::sync::Mutex;
use std::time::Instant;

use crate::domain::vision::analysis::perform_segmentation;
use crate::domain::vision::types::{
    ImageFilterWrapper, ProcessedImage, Rect, SegmentationConfig, VisionError,
};

/// 有状态的图像处理会话
/// 避免了每次操作都在 Kotlin 和 Rust 之间搬运像素数据
#[derive(uniffi::Object)]
pub struct ImageSession {
    // 只有当前图片，如果需要撤销重做，可以使用 Vec<DynamicImage> 作为历史栈
    image: Mutex<DynamicImage>,
}

#[uniffi::export]
impl ImageSession {
    /// 创建新会话
    #[uniffi::constructor]
    pub fn new(mut pixels: Vec<u8>, width: i32, height: i32) -> Result<Self, VisionError> {
        let start = Instant::now();

        let width_u32 = width as u32;
        let height_u32 = height as u32;
        let expected_len = (width_u32 * height_u32 * 4) as usize;

        if pixels.len() != expected_len {
            return Err(VisionError::LoadError("Pixel data mismatch".into()));
        }

        // 格式转换 BGRA -> RGBA
        bgra_to_rgba_in_place(&mut pixels);

        let img_buffer = ImageBuffer::<Rgba<u8>, Vec<u8>>::from_raw(width_u32, height_u32, pixels)
            .ok_or_else(|| VisionError::LoadError("Failed to create image buffer".into()))?;

        info!("KtSession initialized in {:?}", start.elapsed());

        Ok(Self {
            image: Mutex::new(DynamicImage::ImageRgba8(img_buffer)),
        })
    }

    /// 应用滤镜 (核心修改点)
    pub fn apply_filter(&self, filter: ImageFilterWrapper) -> Result<(), VisionError> {
        let start = Instant::now();
        let mut guard = self
            .image
            .lock()
            .map_err(|_| VisionError::ProcessError("Lock failed".into()))?;

        // ✅ 核心改变：直接调用 types.rs 中定义的 apply 方法
        // 这里的 &*guard 是获取 MutexGuard 中的 DynamicImage 引用
        let new_img = filter
            .apply(&*guard)
            .map_err(|e| VisionError::ProcessError(e.to_string()))?;

        info!("apply filter: {:?} in {:?}", filter, start.elapsed());
        // 更新当前状态
        *guard = new_img;
        Ok(())
    }

    /// 获取当前图片数据
    pub fn get_image(&self) -> Result<ProcessedImage, VisionError> {
        let guard = self
            .image
            .lock()
            .map_err(|_| VisionError::ProcessError("Lock failed".into()))?;

        let rgba = guard.to_rgba8();
        Ok(ProcessedImage {
            width: rgba.width() as i32,
            height: rgba.height() as i32,
            pixels: rgba.into_raw(),
        })
    }

    /// ✅ 新增：执行切割
    /// 注意：这里没有修改 self.image，而是返回 Vec<Rect>
    pub fn segmentation(&self, config: SegmentationConfig) -> Result<Vec<Rect>, VisionError> {
        // 1. 获取锁 (只读访问即可，但 Mutex 通常不分读写，除非用 RwLock)
        let guard = self
            .image
            .lock()
            .map_err(|_| VisionError::ProcessError("Lock failed".into()))?;

        // 2. 调用核心算法 (需确保 perform_segmentation 接受 &DynamicImage)
        let rects = perform_segmentation(&*guard, &config);
        Ok(rects)
    }
}

// 复用 helper
fn bgra_to_rgba_in_place(data: &mut [u8]) {
    for chunk in data.chunks_exact_mut(4) {
        chunk.swap(0, 2);
    }
}
