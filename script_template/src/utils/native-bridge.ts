// 定义接口类型
interface AndroidTouchHelper {
  saveScript(script: string): void;
  log(msg: string): void;
}

// 1. Mock 实现 (用于浏览器调试)
const MockBridge: AndroidTouchHelper = {
  saveScript: (s) => console.log(`[Mock] Saved script (${s.length} chars)`),
  log: (msg) => console.log(`[Mock Log] ${msg}`)
};

// 2. 导出单例
// @ts-ignore
export const Bridge: any = (window as any).TouchHelper || MockBridge;
