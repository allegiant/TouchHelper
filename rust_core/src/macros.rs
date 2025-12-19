// ==========================================================================
// 🚀 核心宏定义 (Trait 模式)
// ==========================================================================
macro_rules! define_shared_constants {
    (
        $(
            $(#[$doc:meta])*
            $name:ident : $type:ty = $value:expr;
        )*
    ) => {
        // 1. 自动生成 Rust 常量定义
        $(
            $(#[$doc])*
            pub const $name: $type = $value;
        )*

        // 2. 自动生成 Java 代码的辅助函数
        pub fn generate_java_definitions() -> String {
            // 定义一个局部 Trait，用来处理不同类型的 Java 代码生成
            trait JavaConstFormat {
                fn java_type(&self) -> &'static str;
                fn java_val(&self) -> String;
            }

            // --- 为不同类型实现 Trait ---

            // 针对字符串 (&str)
            impl JavaConstFormat for &str {
                fn java_type(&self) -> &'static str { "String" }
                fn java_val(&self) -> String { format!("{:?}", self) } // 自动加引号
            }

            // 针对整数 (usize, i32, u32)
            impl JavaConstFormat for usize {
                fn java_type(&self) -> &'static str { "int" }
                fn java_val(&self) -> String { format!("{}", self) }
            }
            impl JavaConstFormat for i32 {
                fn java_type(&self) -> &'static str { "int" }
                fn java_val(&self) -> String { format!("{}", self) }
            }
            impl JavaConstFormat for u32 {
                fn java_type(&self) -> &'static str { "int" }
                fn java_val(&self) -> String { format!("{}", self) }
            }

            // 针对 Byte (u8) - 只有这里会编译 Hex 格式化
            impl JavaConstFormat for u8 {
                fn java_type(&self) -> &'static str { "byte" }
                fn java_val(&self) -> String { format!("(byte) 0x{:02X}", self) }
            }

            // 针对布尔 (bool)
            impl JavaConstFormat for bool {
                fn java_type(&self) -> &'static str { "boolean" }
                fn java_val(&self) -> String { format!("{}", self) }
            }

            // 针对浮点 (f32)
            impl JavaConstFormat for f32 {
                fn java_type(&self) -> &'static str { "float" }
                fn java_val(&self) -> String { format!("{}f", self) }
            }

            // 3. 循环生成代码
            let mut sb = String::new();
            $(
                {
                    // 强制类型匹配，利用 Rust 的 Trait 自动分发
                    let val: $type = $value;
                    sb.push_str(&format!("    public static final {} {} = {};\n",
                        val.java_type(),
                        stringify!($name),
                        val.java_val()
                    ));
                }
            )*
            sb
        }
    };
}
