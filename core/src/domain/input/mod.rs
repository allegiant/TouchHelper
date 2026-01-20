use std::process::Command;

use crate::common::types::AccessibilityService;

/// 🎮 输入控制策略接口
/// 无论是 Root 还是无障碍，都必须实现这些基础操作
pub trait InputController: Send + Sync {
    fn click(&self, x: i32, y: i32);
    fn swipe(&self, points: &Vec<Vec<i32>>, duration_ms: u64);
    fn input_text(&self, text: &str);
    fn key_event(&self, key_code: i32);
    fn shell(&self, cmd: &str); // 只有 Root 能真正执行，无障碍模式只记录日志
}

// ==================================================
// 🚀 策略 A: Root 模式 (使用 su 命令)
// ==================================================
pub struct RootStrategy;

impl InputController for RootStrategy {
    fn click(&self, x: i32, y: i32) {
        // 建议未来优化为写 /dev/input/event，这里先保持 su 实现
        let _ = Command::new("su")
            .arg("-c")
            .arg(format!("input tap {} {}", x, y))
            .output();
    }

    fn swipe(&self, points: &Vec<Vec<i32>>, duration_ms: u64) {
        // Root 滑动命令: input swipe x1 y1 x2 y2 duration
        if points.len() < 2 {
            return;
        }
        let start = &points[0];
        let end = &points[points.len() - 1]; // 简化处理，只取首尾，复杂路径需拆分
        let _ = Command::new("su")
            .arg("-c")
            .arg(format!(
                "input swipe {} {} {} {} {}",
                start[0], start[1], end[0], end[1], duration_ms
            ))
            .output();
    }

    fn input_text(&self, text: &str) {
        let _ = Command::new("su")
            .arg("-c")
            .arg(format!("input text \"{}\"", text))
            .output();
    }

    fn key_event(&self, key_code: i32) {
        let _ = Command::new("su")
            .arg("-c")
            .arg(format!("input keyevent {}", key_code))
            .output();
    }

    fn shell(&self, cmd: &str) {
        let _ = Command::new("su").arg("-c").arg(cmd).output();
    }
}

// ==================================================
// ♿ 策略 B: 无障碍模式 (Callback 回调 Kotlin)
// ==================================================
pub struct AccessibilityStrategy {
    // 必须持有回调引用，以便通知 App 层
    service: Box<dyn AccessibilityService>,
}

impl AccessibilityStrategy {
    pub fn new(service: Box<dyn AccessibilityService>) -> Self {
        Self { service }
    }
}

impl InputController for AccessibilityStrategy {
    fn click(&self, x: i32, y: i32) {
        self.service.dispatch_click(x, y);
    }

    fn swipe(&self, points: &Vec<Vec<i32>>, duration_ms: u64) {
        // 暂时只打印日志，需要你在 PlatformCallback 加接口
        log::warn!(
            "[Accessibility] Swipe requested: {:?} over {}ms",
            points,
            duration_ms
        );
        // self.callback.dispatch_swipe(...) // TODO: 需要扩展 servie 接口
    }

    fn input_text(&self, text: &str) {
        // 无障碍输入文字比较麻烦（需要粘贴板或AccessibilityNodeInfo），暂时 Log
        let msg = format!("[Accessibility] Input text not fully implemented: {}", text);

        log::warn!("input_text 功能待实现,暂时只打印日志: {}", msg);
    }

    fn key_event(&self, key_code: i32) {
        let msg = format!(
            "[Accessibility] Key event {} not supported without Root",
            key_code
        );
        log::warn!("key_event 功能待实现,暂时只打印日志: {}", msg);
    }

    fn shell(&self, cmd: &str) {
        let msg = format!(
            "[Permission Denied] Cannot execute shell in Accessibility mode: {}",
            cmd
        );

        log::warn!("shell 功能待实现,暂时只打印日志: {}", msg);
    }
}
