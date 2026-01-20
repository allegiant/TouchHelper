use log::LevelFilter;

#[cfg(target_os = "android")]
use android_logger::{init_once, Config};

#[cfg(not(target_os = "android"))]
use simple_logger::SimpleLogger;

/// 初始化日志系统
/// 自动适配：Android -> Logcat, Desktop -> Console (带颜色/时间)
pub fn init_logger() {
    // ----------------------------------------------------------------
    // 1. Android 平台
    // ----------------------------------------------------------------
    #[cfg(target_os = "android")]
    {
        init_once(
            Config::default()
                .with_max_level(LevelFilter::Debug)
                .with_tag("Touch Core"),
        );
    }

    // ----------------------------------------------------------------
    // 2. 非 Android 平台 (Desktop/iOS)
    // ----------------------------------------------------------------
    #[cfg(not(target_os = "android"))]
    {
        // 使用 simple_logger
        // with_level: 设置日志级别 (Debug/Info/Error)
        // with_colors: 开启控制台颜色
        // init(): 初始化，返回 Result
        let _ = SimpleLogger::new()
            .with_level(LevelFilter::Debug)
            .with_colors(true)
            .with_utc_timestamps() // 或者 .with_local_timestamps()
            .init();

        // 注意：这里使用 `let _ = ...` 是为了忽略 "Logger already initialized" 错误
        // 这样即使多次调用 init_logger 也不会崩溃
    }
}
