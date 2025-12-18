// 定义两端共享的常量
// 这些值将同时用于 Rust 逻辑和生成的 Java 代码
//
// 1. 共享内存文件路径
pub const SHARED_FILE_PATH: &str = "/data/local/tmp/screen_buffer.raw";
// 2. Java Server 的完整类名 (Rust 启动命令需要，Java 本身也可以校验)
pub const SERVER_CLASS_NAME: &str = "org.eu.freex.server.Main";
// 3. 通信信号字节 (Rust 读，Java 写)
pub const SIGNAL_BYTE: u8 = 0xAA;
// 4. 共享内存大小 (4MB)
pub const SHARED_MEMORY_SIZE: usize = 4 * 1024 * 1024;
// android中NativeLib的路径
pub const NATVIE_LIB_PATH: &str = "org/eu/freex/app/NativeLib";
