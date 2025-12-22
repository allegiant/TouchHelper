// 定义接口类型
interface AndroidTouchHelper {
  runScript(script: string): void;
  stopScript(): void;             // 新增
  pauseScript(paused: boolean): void; // 新增
  checkEnvironment(): boolean; // 新增
  setConfig(key: string, value: string): void;
  log(msg: string): void;
}

// 1. Mock 实现 (用于浏览器调试)
const MockBridge: AndroidTouchHelper = {
  runScript: (script) => {
    console.log('%c[Mock] Run:', 'color: green', script.slice(0, 50) + '...');
    alert('Mock: 脚本已运行');
  },
  stopScript: () => console.log('[Mock] Stop Script'),
  pauseScript: (p) => console.log(`[Mock] Pause: ${p}`),
  checkEnvironment: () => {
    console.log(`[Mock] Check Env: Root state`);
    return true; // 浏览器总是返回 true
  },
  setConfig: (key, value) => console.log(`[Mock] Config: ${key}=${value}`),
  log: (msg) => console.log(`[Mock Log] ${msg}`)
};

// 2. 导出单例
// @ts-ignore
export const Bridge: AndroidTouchHelper = window.TouchHelper || MockBridge;
