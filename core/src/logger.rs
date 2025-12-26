use android_logger::{init_once, Config};

pub fn init_android_logger() {
    init_once(
        Config::default()
            .with_max_level(log::LevelFilter::Info)
            .with_tag("RustLogic"),
    );
}
