use std::env;
use std::path::PathBuf;
use touch_core::bindings::generators::java_server_bindgen;

fn main() {
    let args: Vec<String> = env::args().collect();

    // 接收命令行第一个参数作为输出路径
    if args.len() < 2 {
        eprintln!("❌ Error: Missing output path argument.");
        eprintln!("Usage: cargo run --bin gen_java_constants -- <output_path>");
        std::process::exit(1);
    }
    let output_path = PathBuf::from(&args[1]);
    java_server_bindgen::export_java_constants(output_path.to_str().unwrap());
}
