// 引用库里的模块

use rust_core::{bindgen, constants};

fn main() {
    println!("🚀 Starting Unified Generation...");

    // 1. 生成 Java 常量 (调用 constants 模块逻辑)
    bindgen::java_server_bindgen::export_java_constants(constants::JAVA_OUTPUT_PATH);

    // 2. 生成 TypeScript 绑定 (调用 export 模块逻辑)
    bindgen::ts_types_bindgen::export_ts_types(constants::TS_OUTPUT_PATH);

    // 3. 生成 Kotlin UniFFI 绑定 (调用 uniffi-bindgen)
    bindgen::kotlin_bindgen::generate_uniffi_bindings();

    println!("🎉 All generation tasks finished successfully!");
}
