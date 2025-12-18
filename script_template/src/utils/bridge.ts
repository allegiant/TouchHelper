import { ElMessage } from 'element-plus';
import type { MacroConfig } from '../types/touch-helper';

export const Bridge = {
  /**
   * 发送配置给 Android/Rust 运行
   */
  run(config: MacroConfig) {
    const json = JSON.stringify(config);

    if (window.TouchHelper) {
      // 真实环境
      window.TouchHelper.runConfig(json);
      ElMessage.success('Script sent to Android Engine');
    } else {
      // 电脑开发环境 (Mock)
      console.log('%c 🤖 Mock Run ', 'background: #222; color: #bada55', config);
      ElMessage.warning('Dev Mode: Script logged to console (Mock)');
    }
  },

  /**
   * 打印日志
   */
  log(msg: string) {
    if (window.TouchHelper) {
      window.TouchHelper.log(msg);
    } else {
      console.log(`[App Log]: ${msg}`);
    }
  }
};
