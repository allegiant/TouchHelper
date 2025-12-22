use crate::api::colors::Colors;
use crate::api::device::Device;
use crate::api::thread::Thread;
use crate::uniffi_binding::CONTROLLER;
use log::{error, info};
use rquickjs::prelude::Func;
use rquickjs::{Class, Ctx, Object, Result};

pub mod colors;
pub mod device;
pub mod thread;

/// 全局函数：日志 (Log 是最常用的，保持全局)
#[rquickjs::function]
pub fn log(msg: String) {
    info!("[JS] {}", msg);
}

// 供子模块使用的辅助函数
pub(crate) fn with_controller<F>(f: F)
where
    F: FnOnce(&dyn crate::input::InputController),
{
    if let Ok(guard) = CONTROLLER.lock() {
        if let Some(ctrl) = guard.as_ref() {
            // 🔥 核心修复：添加 .as_ref()
            // ctrl 是 &Box<dyn InputController>
            // ctrl.as_ref() 变成了 &dyn InputController
            f(ctrl.as_ref());
        } else {
            error!("[JS] Controller not initialized");
        }
    }
}

/// 注册所有类和全局函数
pub fn register_globals<'js>(globals: &Object<'js>, ctx: &Ctx<'js>) -> Result<()> {
    // 1. 注册全局函数
    globals.set("log", Func::new(log))?;

    // 2. 注册类 (Class Definition)
    Class::<Colors>::define(globals)?;
    Class::<Device>::define(globals)?;
    Class::<Thread>::define(globals)?;

    // 将实例绑定到全局变量
    globals.set("Colors", Class::instance(ctx.clone(), Colors::new()))?;
    globals.set("Device", Class::instance(ctx.clone(), Device::new()))?;
    globals.set("Thread", Class::instance(ctx.clone(), Thread::new()))?;

    Ok(())
}
