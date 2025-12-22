<template>
  <div class="app-container">
    <header>
      <h2>传奇小助手</h2>
    </header>

    <div class="card config-card">
      <div class="form-grid">
        <div class="form-item">
          <label>循环次数:</label>
          <input type="number" v-model="loopCount">
        </div>
      </div>
    </div>

    <div class="card status-card">
      <div class="status-indicator" :class="{ running: isRunning }">
        {{ isRunning ? '🚀 脚本运行中' : '💤 等待启动' }}
      </div>
    </div>

    <div class="actions-bar">
      <button v-if="!isRunning" class="btn-primary" @click="run">
        ▶️ 启动挂机
      </button>
      <div v-else class="running-controls">
        <button class="btn-warning" @click="togglePause">
          {{ isPaused ? '▶️ 继续' : '⏸ 暂停' }}
        </button>
        <button class="btn-danger" @click="stop">
          ⏹ 停止
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { Bridge } from './utils/native-bridge';

// 状态
const loopCount = ref(5);
const isRunning = ref(false);
const isPaused = ref(false);

// 同步配置
function syncConfig() {
  Bridge.setConfig('loop_times', String(loopCount.value));
}

// 🔥 核心运行逻辑：极简！
async function run() {
  // 1. 检查环境 (Root/无障碍)
  if (!Bridge.checkEnvironment()) return;

  syncConfig();

  try {
    // 2. 直接获取预编译好的脚本文件
    // 无论你在 tools/build.js 里配的是哪个游戏，这里永远只读 script.js
    const response = await fetch('/script.js');
    if (!response.ok) throw new Error("加载脚本文件失败");

    const scriptContent = await response.text();

    // 3. 下发执行
    Bridge.runScript(scriptContent);
    isRunning.value = true;
    isPaused.value = false;

  } catch (e: any) {
    alert("启动失败: " + e.message);
  }
}

function stop() {
  if (confirm("确定停止吗？")) {
    Bridge.stopScript();
    isRunning.value = false;
  }
}

function togglePause() {
  isPaused.value = !isPaused.value;
  Bridge.pauseScript(isPaused.value);
}
</script>

<style scoped>
.status-indicator {
  text-align: center;
  padding: 20px;
  font-weight: bold;
  background: #eee;
  border-radius: 8px;
}

.status-indicator.running {
  background: #e6f7ff;
  color: #1890ff;
}

.actions-bar {
  position: fixed;
  bottom: 0;
  width: 100%;
  padding: 16px;
  background: white;
  display: flex;
  gap: 10px;
}

.btn-primary {
  flex: 1;
  padding: 12px;
  border-radius: 8px;
  border: none;
  background: #1890ff;
  color: white;
  font-size: 16px;
}

.running-controls {
  display: flex;
  flex: 1;
  gap: 10px;
}

.btn-warning,
.btn-danger {
  flex: 1;
  border: none;
  color: white;
  border-radius: 8px;
}

.btn-warning {
  background: #fa8c16;
}

.btn-danger {
  background: #ff4d4f;
}

.mode-switch {
  display: flex;
  background: #f0f2f5;
  padding: 4px;
  border-radius: 8px;
  margin-bottom: 8px;
}

.mode-switch label {
  flex: 1;
  text-align: center;
  padding: 8px;
  cursor: pointer;
  border-radius: 6px;
  transition: all 0.2s;
  font-weight: 500;
}

.mode-switch label.active {
  background: white;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  color: #1890ff;
}

.mode-switch input {
  display: none;
}

.mode-hint {
  font-size: 12px;
  color: #666;
  text-align: center;
  margin-bottom: 8px;
}

.status-tag {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
  background: #eee;
}

.status-tag.running {
  background: #e6f7ff;
  color: #1890ff;
}

.status-tag.paused {
  background: #fff7e6;
  color: #fa8c16;
}

.actions-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 16px;
  background: white;
  border-top: 1px solid #eee;
  display: flex;
  gap: 10px;
}

.btn-primary {
  width: 100%;
  padding: 12px;
  background: #1890ff;
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 16px;
}

.running-controls {
  display: flex;
  gap: 10px;
  width: 100%;
}

.btn-warning {
  flex: 1;
  background: #fa8c16;
  color: white;
  border: none;
  border-radius: 8px;
  padding: 12px;
}

.btn-danger {
  flex: 1;
  background: #ff4d4f;
  color: white;
  border: none;
  border-radius: 8px;
  padding: 12px;
}

.app-container {
  padding-bottom: 80px;
  /* 防止底部栏遮挡 */
}
</style>
