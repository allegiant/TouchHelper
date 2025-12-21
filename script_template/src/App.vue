<template>
  <div class="app-container">
    <header>
      <h2>TouchHelper Pro</h2>
      <select v-model="selectedGame" class="game-select">
        <option value="Legend">传奇脚本 (Legend)</option>
        <option value="Custom">自定义 (编辑器)</option>
      </select>
    </header>

    <div class="card config-card">
    </div>

    <div class="card script-card">
      <div class="card-header">
        <h3>
          {{ selectedGame === 'Custom' ? '📝 编辑代码' : '📦 编译预览' }}
        </h3>
      </div>
      <div class="editor-container">
        <textarea v-model="displayContent" :readonly="selectedGame !== 'Custom'"
          :placeholder="selectedGame !== 'Custom' ? '点击运行后这里将显示编译后的代码...' : '在此编写...'"></textarea>
      </div>
    </div>

    <div class="actions">
      <button class="btn-primary" @click="run" :disabled="isCompiling">
        {{ isCompiling ? '⏳ 编译中...' : '▶️ 编译并运行' }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { Bridge } from './utils/native-bridge';
import { bundleScript } from './utils/bundler';

// 1. 动态加载 src/scripts 下的所有 TS 文件源码
// eager=true 确保直接拿到内容字符串
const rawGameFiles = import.meta.glob('./scripts/**/*.ts', { as: 'raw', eager: true });

const loopCount = ref(5);
const enableBoss = ref(true);
const targetColor = ref("#ff0000");

const selectedGame = ref('Legend');
const customCode = ref('// 在此写简单的单文件代码\nlog("Hello");');
const compiledCode = ref(''); // 存储编译后的结果
const isCompiling = ref(false);

// 显示逻辑：如果是Custom模式显示用户代码，否则显示编译结果
const displayContent = computed({
  get: () => selectedGame.value === 'Custom' ? customCode.value : compiledCode.value,
  set: (v) => { if (selectedGame.value === 'Custom') customCode.value = v; }
});

// 同步配置 (原有逻辑)
function syncAllConfigs() {
  Bridge.setConfig('loop_times', String(loopCount.value));
  Bridge.setConfig('enable_boss', String(enableBoss.value));
  Bridge.setConfig('target_color', targetColor.value);
}

// 核心运行逻辑
// 修改 run 函数部分
async function run() {
  syncAllConfigs();
  isCompiling.value = true;

  try {
    let finalScript = '';

    if (selectedGame.value === 'Custom') {
      finalScript = customCode.value;
    } else {
      const gamePrefix = `./scripts/${selectedGame.value}/`;
      const files: Record<string, string> = {};

      console.log(`[Vue] 正在扫描路径前缀: ${gamePrefix}`);

      // 遍历所有读取到的文件
      for (const path in rawGameFiles) {
        // 打印所有发现的文件路径，方便调试
        // console.log(`[Vue] 发现文件: ${path}`);

        if (path.startsWith(gamePrefix)) {
          const virtualPath = path.replace(gamePrefix, '/');
          // 确保内容是字符串
          const content = rawGameFiles[path];
          files[virtualPath] = typeof content === 'string' ? content : String(content);
        }
      }

      // 🔥 关键调试：打印最终生成的文件映射表 Key
      console.log('[Vue] 虚拟文件系统 Keys:', JSON.stringify(Object.keys(files)));

      if (!files['/index.ts']) {
        alert(`错误：未找到入口文件 /index.ts\n请检查 src/scripts/${selectedGame.value}/ 目录`);
        isCompiling.value = false;
        return;
      }

      finalScript = await bundleScript(files);
      compiledCode.value = finalScript;
    }

    if (!finalScript || finalScript.length === 0) {
      alert("严重错误：打包产物为空！请检查控制台日志。");
      return;
    }

    Bridge.log(`[Vue] 下发脚本长度: ${finalScript.length}`);
    Bridge.runScript(finalScript);

  } catch (e: any) {
    alert("执行/编译错误: " + e.message);
    console.error(e);
  } finally {
    isCompiling.value = false;
  }
}
</script>

<style scoped>
.game-select {
  padding: 8px;
  border-radius: 4px;
  border: 1px solid #ccc;
  font-size: 14px;
}

.card {
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  margin-bottom: 16px;
  overflow: hidden;
}

.card-header {
  padding: 12px 16px;
  background: #fafafa;
  border-bottom: 1px solid #eee;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header h3 {
  margin: 0;
  font-size: 1rem;
  font-weight: 600;
}

.form-grid {
  padding: 16px;
  display: grid;
  gap: 12px;
}

.form-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

/* 特殊高亮 Root 选项 */
.highlight-item {
  background-color: #fff1f0;
  margin: -8px -16px 8px -16px;
  /* 负边距拉伸背景 */
  padding: 12px 16px;
  border-bottom: 1px solid #ffccc7;
}

.label-with-desc {
  display: flex;
  flex-direction: column;
}

.label-with-desc .desc {
  font-size: 0.75rem;
  color: #ff4d4f;
  margin-top: 2px;
}

.divider {
  height: 1px;
  background-color: #eee;
  margin: 4px 0;
}

input[type="number"],
input[type="text"] {
  padding: 8px;
  border: 1px solid #ddd;
  border-radius: 6px;
  width: 100px;
  text-align: right;
}

.color-picker-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
}

.color-picker-wrapper input[type="color"] {
  border: none;
  padding: 0;
  width: 32px;
  height: 32px;
  background: none;
  cursor: pointer;
}

/* 开关样式 */
.switch {
  position: relative;
  display: inline-block;
  width: 44px;
  height: 24px;
}

.switch input {
  opacity: 0;
  width: 0;
  height: 0;
}

.slider {
  position: absolute;
  cursor: pointer;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: #ccc;
  transition: .4s;
}

.slider:before {
  position: absolute;
  content: "";
  height: 18px;
  width: 18px;
  left: 3px;
  bottom: 3px;
  background-color: white;
  transition: .4s;
}

input:checked+.slider {
  background-color: #1890ff;
  /* 蓝色 */
}

/* Root 模式开启时显示为警告色 */
.highlight-item input:checked+.slider {
  background-color: #ff4d4f;
  /* 红色 */
}

input:checked+.slider:before {
  transform: translateX(20px);
}

.slider.round {
  border-radius: 34px;
}

.slider.round:before {
  border-radius: 50%;
}

.btn-small {
  padding: 6px 12px;
  font-size: 12px;
  border: 1px solid #d9d9d9;
  background: white;
  border-radius: 4px;
  cursor: pointer;
}
</style>
