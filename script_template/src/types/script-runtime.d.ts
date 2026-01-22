// 脚本运行时环境定义 (单例模式版)
// script_template/src/types/script-runtime.d.ts

// 1. 引入 Rust 自动生成的类型 (确保文件名一致)
import { ImageFilterWrapper, SegmentationConfig } from './touch-helper';

// 脚本运行时环境定义 (单例模式版)
declare global {
  /** 全局日志函数 */
  function log(msg: string): void;

  // ============================================================
  // ✨ 新增：JsImage 类定义
  // ============================================================
  class JsImage {
    readonly width: number;
    readonly height: number;

    /**
     * 应用滤镜 (通用入口)
     * IDE 会根据 ImageFilterWrapper 自动提示所有可用滤镜！
     */
    applyFilter(filter: ImageFilterWrapper): void;

    /**
     * OCR 网格识别
     * @param config 切割配置 (来自 touch-helper)
     * @param library 字库 { "A": "0101..." }
     * @param minConf 相似度阈值
     */
    ocrGrid(
      config: SegmentationConfig,
      library: Record<string, string>,
      minConf: number
    ): string;

    /** 裁剪图片 */
    crop(x: number, y: number, w: number, h: number): void;

    /** 保存图片 (调试用) */
    save(path: string): void;
  }

  // ============================================================
  // --- Device 单例 (已扩展) ---
  // ============================================================
  interface DeviceInstance {
    click(x: number, y: number): void;
    swipe(x1: number, y1: number, x2: number, y2: number, duration: number): void;
    shell(cmd: string): string;

    /** * ✨ 新增：截取当前屏幕 
     * @returns 返回 Rust 增强版 JsImage 对象
     */
    capture(): JsImage;
  }
  /** 全局设备对象 */
  var Device: DeviceInstance;

  // ============================================================
  // --- Colors 单例 (保持不变) ---
  // ============================================================
  interface ColorsInstance {
    findColor(color: string): boolean;
    findColorPoint(color: string): number[] | null;
  }
  /** 全局颜色工具 */
  var Colors: ColorsInstance;

  // ============================================================
  // --- Config 单例 (保持不变) ---
  // ============================================================
  interface ConfigInstance {
    get(key: string): string;
    getInt(key: string): number;
  }
  /** 全局配置对象 */
  var Config: ConfigInstance;

  // ============================================================
  // --- Thread 单例 (保持不变) ---
  // ============================================================
  interface ThreadInstance {
    sleep(ms: number): Promise<void>;
  }
  /** 全局线程工具 */
  var Thread: ThreadInstance;
}

export { };
