// 全局对象定义
export { };

declare global {
  interface Window {
    TouchHelper?: {
      /**
       * 发送 JSON 字符串给安卓
       */
      runConfig(json: string): void;
      /**
       * 打印日志到 Logcat
       */
      log(msg: string): void;
    };
  }
}
