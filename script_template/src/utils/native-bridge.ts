// 1. Mock 实现 (用于浏览器调试)
// 注意：这里显式指明类型为 AndroidTouchHelper (来自 global.d.ts)
const MockBridge: AndroidTouchHelper = {
  runScript: (script) => {
    console.groupCollapsed('%c[Mock] Run Script', 'color: blue');
    console.log(script);
    console.groupEnd();
    alert('脚本已发送 (Mock)');
  },
  setConfig: (key, value) => {
    console.log(`[Mock] Config: ${key}=${value}`);
  },
  log: (msg) => {
    console.log(`[Mock Log] ${msg}`);
  }
};

// 2. 导出单例
// 如果 window.TouchHelper 存在则使用它，否则使用 Mock
export const Bridge: AndroidTouchHelper = window.TouchHelper || MockBridge;
