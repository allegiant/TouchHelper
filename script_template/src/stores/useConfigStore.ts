// script_template/src/stores/useConfigStore.ts
import { defineStore } from 'pinia';
import { DEFAULT_CONFIG } from '../scripts/Legend/config';

export const useConfigStore = defineStore('scriptConfig', {
  state: () => ({
    // 初始化时拷贝一份默认配置
    config: { ...DEFAULT_CONFIG }
  }),
  persist: true, // 开启持久化 (需在 main.ts 注册插件)
  actions: {
    reset() {
      this.config = { ...DEFAULT_CONFIG };
    }
  }
});
