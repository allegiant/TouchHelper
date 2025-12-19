use jni::{
    objects::{JByteBuffer, JClass},
    JNIEnv,
};

use crate::core::SCREEN_BUFFER;

// 必须保留：用于接收 Java 传来的预览图（如果有）
#[no_mangle]
pub unsafe extern "C" fn Java_org_eu_freex_app_NativeLib_updateScreenBuffer(
    env: JNIEnv,
    _class: JClass,
    buffer: JByteBuffer,
    _w: i32,
    _h: i32,
    _stride: i32,
) {
    let addr = match env.get_direct_buffer_address(&buffer) {
        Ok(a) => a,
        Err(_) => return,
    };
    let len = match env.get_direct_buffer_capacity(&buffer) {
        Ok(l) => l,
        Err(_) => return,
    };
    if let Ok(guard) = SCREEN_BUFFER.lock() {
        if !guard.0.is_empty() {
            let min_len = std::cmp::min(guard.0.len(), len);
            std::ptr::copy_nonoverlapping(guard.0.as_ptr(), addr, min_len);
        }
    }
}

// 必须保留：用于无障碍模式下的录屏推流
#[no_mangle]
pub unsafe extern "C" fn Java_org_eu_freex_app_NativeLib_pushScreenImage(
    env: JNIEnv,
    _class: JClass,
    buffer: JByteBuffer,
    width: i32,
    height: i32,
    _pixel_stride: i32,
    row_stride: i32,
    scale: f32,
) {
    let addr = match env.get_direct_buffer_address(&buffer) {
        Ok(a) => a,
        Err(_) => return,
    };
    let len = match env.get_direct_buffer_capacity(&buffer) {
        Ok(l) => l,
        Err(_) => return,
    };
    let src_slice = std::slice::from_raw_parts(addr, len);

    if let Ok(mut guard) = SCREEN_BUFFER.lock() {
        if guard.0.len() != len {
            guard.0.resize(len, 0);
        }
        guard.0.copy_from_slice(src_slice);
        guard.1 = width as usize;
        guard.2 = height as usize;
        guard.3 = row_stride as usize;
        guard.4 = scale;
    }
}
