use std::sync::{Arc, Mutex};
// 引入必要的类型，包括 Atom 和 Error
use image::{DynamicImage, GenericImageView};
use rquickjs::{class::Trace, Ctx, Exception, JsLifetime, Object, Result, Value};
use serde_json;

// 引入您在 domain 中定义的类型
use crate::domain::vision::analysis::perform_segmentation;
use crate::domain::vision::types::{ImageFilterWrapper, SegmentationConfig};

/// 脚本使用的图像对象
#[derive(Trace, JsLifetime)]
#[rquickjs::class]
pub struct JsImage {
    #[qjs(skip_trace)]
    inner: Arc<Mutex<DynamicImage>>,
}

impl JsImage {
    pub fn new(img: DynamicImage) -> Self {
        Self {
            inner: Arc::new(Mutex::new(img)),
        }
    }
}

#[rquickjs::methods]
impl JsImage {
    // ========================================================================
    // 1. 基础属性
    // ========================================================================
    #[qjs(get)]
    pub fn width(&self) -> u32 {
        self.inner.lock().unwrap().width()
    }

    #[qjs(get)]
    pub fn height(&self) -> u32 {
        self.inner.lock().unwrap().height()
    }

    // ========================================================================
    // 2. 通用滤镜入口
    // ========================================================================

    #[qjs(rename = "applyFilter")]
    pub fn apply_filter<'js>(&self, ctx: Ctx<'js>, filter_val: Value<'js>) -> Result<()> {
        let mut guard = self.inner.lock().unwrap();

        // 1. JS Value -> JSON String
        let maybe_js_string = ctx.json_stringify(filter_val)?;
        let js_string = match maybe_js_string {
            Some(s) => s,
            None => {
                return Self::throw_err(&ctx, "Filter args invalid (undefined or cannot stringify)")
            }
        };

        let json_str = js_string.to_string()?;

        // 2. JSON String -> Rust Enum
        let wrapper: ImageFilterWrapper = serde_json::from_str(&json_str).map_err(|e| {
            let msg = format!("Filter args parse error: {}", e);
            // unwrap_err 是安全的，因为 throw_err_result 永远返回 Err
            Self::throw_err_result::<()>(&ctx, &msg).unwrap_err()
        })?;

        // 3. 执行滤镜
        match wrapper.apply(&guard) {
            Ok(processed) => {
                *guard = processed;
                Ok(())
            }
            Err(e) => Self::throw_err(&ctx, &format!("Filter execution failed: {}", e)),
        }
    }

    // ========================================================================
    // 3. OCR 网格识别
    // ========================================================================

    #[qjs(rename = "ocrGrid")]
    pub fn ocr_grid<'js>(
        &self,
        ctx: Ctx<'js>,
        config_val: Value<'js>,
        library: Object<'js>,
        min_conf: f32,
    ) -> Result<String> {
        let guard = self.inner.lock().unwrap();

        // 1. 解析 Config
        let maybe_js_string = ctx.json_stringify(config_val)?;
        let js_string = match maybe_js_string {
            Some(s) => s,
            None => return Self::throw_err(&ctx, "Config invalid (undefined or cannot stringify)"),
        };
        let json_str = js_string.to_string()?;

        let config: SegmentationConfig = serde_json::from_str(&json_str).map_err(|e| {
            Self::throw_err_result::<SegmentationConfig>(
                &ctx,
                &format!("Config parse error: {}", e),
            )
            .unwrap_err()
        })?;

        // 2. 解析字库 (迭代 Object)
        let mut rust_lib: Vec<(String, String)> = Vec::new();

        for item in library {
            // item 是 Result<(Atom, Value)>
            let (key_atom, val_value) = item?;

            // Atom -> String
            let key = key_atom.to_string()?;

            // Value -> String (确保 Value 是字符串)
            let val_js_string = match val_value.as_string() {
                Some(s) => s,
                None => {
                    return Self::throw_err(
                        &ctx,
                        "Library value must be a string (e.g., '0101...')",
                    )
                }
            };
            let val = val_js_string.to_string()?;

            rust_lib.push((key, val));
        }

        // 3. 调用核心算法
        let rects = perform_segmentation(&guard, &config);

        // 4. 识别循环
        let mut result_str = String::new();
        let (img_w, img_h) = guard.dimensions();

        for rect in rects {
            // 越界检查
            if rect.left < 0 || rect.top < 0 {
                continue;
            }
            if (rect.left as u32 + rect.width) > img_w || (rect.top as u32 + rect.height) > img_h {
                continue;
            }

            // 切图
            let sub_img = image::imageops::crop_imm(
                &*guard,
                rect.left as u32,
                rect.top as u32,
                rect.width,
                rect.height,
            );

            let sub_dynamic = DynamicImage::ImageRgba8(sub_img.to_image());
            let feature = generate_binary_feature_internal(&sub_dynamic);

            // 匹配
            let mut best_char: &str = "";
            let mut max_score = 0.0f32;

            for (char_str, lib_feat) in &rust_lib {
                let score = calculate_similarity(feature.as_str(), lib_feat.as_str());
                if score > max_score {
                    max_score = score;
                    best_char = char_str.as_str();
                }
            }

            if max_score >= min_conf {
                result_str.push_str(best_char);
            }
        }

        Ok(result_str)
    }

    // ========================================================================
    // 4. 其他方法
    // ========================================================================

    pub fn crop<'js>(&self, ctx: Ctx<'js>, x: i32, y: i32, w: u32, h: u32) -> Result<()> {
        let mut guard = self.inner.lock().unwrap();
        if x < 0 || y < 0 {
            return Self::throw_err(&ctx, "x, y must be >= 0");
        }
        let cropped = image::imageops::crop_imm(&*guard, x as u32, y as u32, w, h).to_image();
        *guard = DynamicImage::ImageRgba8(cropped);
        Ok(())
    }

    pub fn save<'js>(&self, ctx: Ctx<'js>, path: String) -> Result<()> {
        let guard = self.inner.lock().unwrap();
        guard.save(&path).map_err(|e| {
            Self::throw_err_result::<()>(&ctx, &format!("Save failed: {}", e)).unwrap_err()
        })
    }
}

// ========================================================================
// 5. 辅助函数
// ========================================================================

impl JsImage {
    /// 辅助方法：抛出 JS 异常并返回 rquickjs::Result<T>
    /// 返回值永远是 Err(Error)，所以泛型 T 可以是任意类型
    fn throw_err<'js, T>(ctx: &Ctx<'js>, msg: &str) -> Result<T> {
        let ex = Exception::from_message(ctx.clone(), msg)?;

        // 🔴 修复点：
        // 1. ex.into() 将 Exception 转换为 Value
        // 2. ctx.throw() 返回 Error (表示 JS 异常已设置)
        // 3. 将其包裹在 Result::Err 中
        Err(ctx.throw(ex.into()))
    }

    fn throw_err_result<'js, T>(ctx: &Ctx<'js>, msg: &str) -> Result<T> {
        Self::throw_err(ctx, msg)
    }
}

fn generate_binary_feature_internal(img: &DynamicImage) -> String {
    let gray = img.to_luma8();
    let (w, h) = gray.dimensions();
    let mut feature = String::with_capacity((w * h) as usize);

    for y in 0..h {
        for x in 0..w {
            let pixel = gray.get_pixel(x, y);
            if pixel[0] > 128 {
                feature.push('1');
            } else {
                feature.push('0');
            }
        }
    }
    feature
}

fn calculate_similarity(s1: &str, s2: &str) -> f32 {
    let len1 = s1.len();
    let len2 = s2.len();

    if len1 == 0 || len2 == 0 {
        return 0.0;
    }

    let min_len = std::cmp::min(len1, len2);
    let max_len = std::cmp::max(len1, len2);

    if (max_len as f32 / min_len as f32) > 1.5 {
        return 0.0;
    }

    let mut match_count = 0;
    let s1_bytes = s1.as_bytes();
    let s2_bytes = s2.as_bytes();

    for i in 0..min_len {
        if s1_bytes[i] == s2_bytes[i] {
            match_count += 1;
        }
    }

    match_count as f32 / max_len as f32
}
