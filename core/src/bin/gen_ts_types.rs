use std::env;
use std::path::PathBuf;

use touch_core::bindings::generators::ts_types_bindgen;

fn main() {
    let args: Vec<String> = env::args().collect();

    if args.len() < 2 {
        eprintln!("❌ Error: Missing output path argument.");
        eprintln!("Usage: cargo run --bin gen_ts_types -- <output_path>");
        std::process::exit(1);
    }

    let output_path = PathBuf::from(&args[1]);

    println!("📘 Generating TypeScript definitions to: {:?}", output_path);

    // 调用生成逻辑
    ts_types_bindgen::export_ts_types(output_path.to_str().unwrap());
}
