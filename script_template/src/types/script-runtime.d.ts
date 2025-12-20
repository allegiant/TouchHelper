// 脚本运行时环境定义 (单例模式版)

declare global {
  /** 全局日志函数 */
  function log(msg: string): void;

  // --- Device 单例 ---
  interface DeviceInstance {
    click(x: number, y: number): void;
    swipe(x1: number, y1: number, x2: number, y2: number, duration: number): void;
    shell(cmd: string): string;
  }
  /** 全局设备对象 (直接使用，无需 new) */
  var Device: DeviceInstance;

  // --- Colors 单例 ---
  interface ColorsInstance {
    findColor(color: string): boolean;
    findColorPoint(color: string): number[] | null;
  }
  /** 全局颜色工具 (直接使用，无需 new) */
  var Colors: ColorsInstance;

  // --- Config 单例 ---
  interface ConfigInstance {
    get(key: string): string;
    getInt(key: string): number;
  }
  /** 全局配置对象 (直接使用，无需 new) */
  var Config: ConfigInstance;

  // --- Thread 单例 ---
  interface ThreadInstance {
    sleep(ms: number): Promise<void>;
  }
  /** 全局线程工具 (直接使用，无需 new) */
  var Thread: ThreadInstance;
}

export { };
