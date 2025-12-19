use std::{path::Path, process::Command};

pub fn generate_uniffi_bindings() {
    println!("🔄 [3/3] Generating UniFFI Kotlin bindings...");

    // Android 项目的 jniLibs 目录 (cargo ndk 会自动把 .so 放到这里)
    let main_dir = "../android/app/src/main";
    let jni_libs_dir = format!("{}/jniLibs", main_dir);

    // 生成绑定时，uniffi 需要读取一个已经编译好的 .so 来提取元数据
    // 我们约定取 arm64-v8a 架构的作为“模板”
    let lib_so_path = format!("{}/arm64-v8a/librust_core.so", jni_libs_dir);
    let kotlin_out_dir = format!("{}/java/generated", main_dir);

    //自动调用 cargo ndk 编译

    println!("🔨 Building Rust library for Android (cargo ndk)...");

    let status = Command::new("cargo")
        .args(&[
            "ndk",
            // 支持的架构列表
            "-t",
            "arm64-v8a",
            "-t",
            "armeabi-v7a",
            "-t",
            "x86_64",
            // 输出目录：直接指定到 Android 项目的 jniLibs
            "-o",
            &jni_libs_dir,
            "build",
            "--release",
        ])
        .status()
        .expect("Failed to run cargo ndk build");

    if !status.success() {
        eprintln!("❌ Rust build failed!");
        std::process::exit(1);
    }
    println!(
        "✅ Rust build successful. Libraries installed to: {}",
        jni_libs_dir
    );

    // 检查一下模板 .so 是否存在
    if !Path::new(&lib_so_path).exists() {
        eprintln!("❌ Error: Compiled library not found at: {}", lib_so_path);
        eprintln!("   Check if cargo-ndk output directory is correct.");
        std::process::exit(1);
    }

    println!("📄 Generating Kotlin bindings using UniFFI...");
    let status = Command::new("cargo")
        .args(&[
            "run",
            "--bin",
            "uniffi-bindgen",
            "generate",
            "--library",
            &lib_so_path, // 读取刚才编译出的 .so
            "--language",
            "kotlin",
            "--no-format", // 忽略 ktlint 警告
            "--out-dir",
            &kotlin_out_dir,
        ])
        .status()
        .expect("Failed to run uniffi-bindgen");

    if status.success() {
        println!("✅ Kotlin bindings generated at: {}", &kotlin_out_dir);
    } else {
        eprintln!("❌ UniFFI generation failed.");
        std::process::exit(1);
    }
}
