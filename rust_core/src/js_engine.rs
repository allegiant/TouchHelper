use crate::api;
use log::info;
use rquickjs::{AsyncContext, AsyncRuntime};

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
    // 支持顶级 await
    let code = format!(
        r#"
        (async () => {{
            try {{
                log("Script Start");
                {}
                log("Script End");
            }} catch(e) {{
                log("Script Error: " + e);
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

