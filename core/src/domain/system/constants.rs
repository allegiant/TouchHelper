use crate::define_shared_constants;

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
}
