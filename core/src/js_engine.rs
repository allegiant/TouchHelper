use crate::api;
use log::info;
use rquickjs::{AsyncContext, AsyncRuntime};
use tokio::task::AbortHandle;

// 🔥 全局任务句柄：用于存储当前正在跑的脚本任务
// 这样我们才能在外部调用 stop_script 时找到它并杀掉
pub static CURRENT_SCRIPT_TASK: std::sync::OnceLock<std::sync::Mutex<Option<AbortHandle>>> =
    std::sync::OnceLock::new();

pub async fn run_script_async(script_content: String) -> Result<(), String> {
    info!("🚀 Initializing JS Runtime (OO Mode)...");

    let rt = AsyncRuntime::new().map_err(|e| e.to_string())?;
    let ctx = AsyncContext::full(&rt).await.map_err(|e| e.to_string())?;

    // 注册 API
    ctx.with(|ctx| {
        let global = ctx.globals();
        // 传入 ctx 以便注册 Class
        if let Err(e) = api::register_globals(&global, &ctx) {
            log::error!("Failed to register globals: {}", e);
        }
    })
    .await;

    // 执行脚本
    let code = format!(
        r#"
        (async () => {{
            try {{
                log("🚀 Script System Initialized");
                
                // 1. 注入脚本内容 (var GameScript = ...)
                {} 

                // 2. 智能入口查找逻辑
                if (typeof GameScript !== 'undefined') {{
                    let entry = null;
                    let entryName = "unknown";

                    // 优先级 A: 检查常用入口名
                    if (GameScript.main) {{ entry = GameScript.main; entryName = "main"; }}
                    else if (GameScript.start) {{ entry = GameScript.start; entryName = "start"; }}
                    else if (GameScript.run) {{ entry = GameScript.run; entryName = "run"; }}
                    
                    // 优先级 B: 如果都没有，遍历导出对象，找第一个是函数的
                    if (!entry) {{
                        for (let key in GameScript) {{
                            if (typeof GameScript[key] === 'function') {{
                                entry = GameScript[key];
                                entryName = key;
                                break;
                            }}
                        }}
                    }}

                    // 3. 执行入口
                    if (entry) {{
                        log("✅ Auto-detected entry point: [" + entryName + "]");
                        await entry(); // <--- 关键：这里 await 保证了脚本不会失控
                    }} else {{
                        log("⚠️ Warning: No exported function found! Did you forget 'export async function...'?");
                    }}

                }} else {{
                    log("⚠️ Warning: GameScript object not found.");
                }}

                log("🏁 Script Finished");
            }} catch(e) {{
                log("❌ Script Error: " + e);
                if (e.stack) {{ log(e.stack); }}
            }}
        }})()
        "#,
        script_content
    );

    ctx.with(|ctx| ctx.eval::<(), _>(code))
        .await
        .map_err(|e| e.to_string())?;

    rt.idle().await;
    Ok(())
}
