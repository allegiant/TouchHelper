use crate::define_shared_constants;

// 生成ts types路径
pub const TS_OUTPUT_PATH: &str = "../script_template/src/types/touch-helper.d.ts";
// 生成server端Java常量路径
pub const JAVA_OUTPUT_PATH: &str =
    "../FreeToucherServer/src/main/java/bind/GeneratedConstants.java";

// 2. Java Server 的完整类名 (Rust 启动命令需要，Java 本身也可以校验)
pub const SERVER_CLASS_NAME: &str = "org.eu.freex.server.Main";

// ==========================================================================
// 📝 在这里添加共享内存配置
// ==========================================================================
define_shared_constants! {
    /// 共享内存文件路径
    SHARED_FILE_PATH: &str = "/data/local/tmp/screen_buffer.raw";

    /// 共享内存大小 (4MB)
    SHARED_MEMORY_SIZE: usize = 4 * 1024 * 1024;

    /// 信号字节 (Sync Byte)
    SIGNAL_BYTE: u8 = 0xAA;

    // android中NativeLib的路径
    NATVIE_LIB_PATH: &str = "org/eu/freex/app/NativeLib";
}
