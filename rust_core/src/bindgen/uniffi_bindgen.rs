use std::{path::Path, process::Command};

fn generate_uniffi_bindings() {
    println!("🔄 [3/3] Generating UniFFI Kotlin bindings...");

    // 配置路径
    let lib_path = "target/aarch64-linux-android/release/librust_core.so";
    let out_dir = "../android/app/src/main/java/generated/org/eu/freex/app";

    // 检查 .so 是否存在 (因为 UniFFI 需要读取 .so)
    if !Path::new(lib_path).exists() {
        eprintln!("⚠️  Warning: Library not found at {}.", lib_path);
        eprintln!("    Skipping UniFFI generation. Please build the project first.");
        return;
    }

    // 调用 uniffi-bindgen (作为子进程调用最稳妥，避免环境干扰)
    let status = Command::new("cargo")
        .args(&[
            "run",
            "--bin",
            "uniffi-bindgen",
            "generate",
            "--library",
            lib_path,
            "--language",
            "kotlin",
            "--out-dir",
            out_dir,
        ])
        .status()
        .expect("Failed to run uniffi-bindgen");

    if status.success() {
        println!("✅ Kotlin bindings generated.");
    } else {
        eprintln!("❌ UniFFI generation failed.");
        std::process::exit(1);
    }
}
