<template>
  <div class="card config-card">
    <div class="card-header">
      <h3>⚙️ 参数配置 (Settings)</h3>
      <button class="btn-small" @click="syncAllConfigs">强制保存</button>
    </div>

    <div class="form-grid">
      <div class="form-item highlight-item">
        <label class="label-with-desc">
          <span>运行模式 (Root Mode)</span>
          <small class="desc">切换后需重启 App 生效</small>
        </label>
        <label class="switch">
          <input type="checkbox" v-model="useRoot" @change="handleRootChange">
          <span class="slider round"></span>
        </label>
      </div>

      <div class="divider"></div>

      <div class="form-item">
        <label>循环次数 (Loop):</label>
        <input type="number" v-model="loopCount" @change="syncConfig('loop_times', loopCount)">
      </div>

      <div class="form-item">
        <label>启用 Boss 模式:</label>
        <label class="switch">
          <input type="checkbox" v-model="enableBoss" @change="syncConfig('enable_boss', enableBoss)">
          <span class="slider round"></span>
        </label>
      </div>

      <div class="form-item">
        <label>目标颜色 (Hex):</label>
        <div class="color-picker-wrapper">
          <input type="color" v-model="targetColor" @change="syncConfig('target_color', targetColor)">
          <span>{{ targetColor }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { Bridge } from '../utils/native-bridge';

// --- 状态定义 ---
const useRoot = ref(false); // 默认关闭，实际应从配置读取
const loopCount = ref(5);
const enableBoss = ref(true);
const targetColor = ref("#ff0000");

// --- 核心方法 ---

// 1. 处理 Root 模式切换 (特殊处理：需要存 SharedPref 并提示重启)
function handleRootChange() {
  const valStr = useRoot.value ? "true" : "false";
  // 调用 Bridge 保存到 SharedPreferences
  Bridge.setConfig("use_root", valStr);

  // 提示用户 (实际生产中可以使用 Toast)
  alert(`模式已切换为 [${useRoot.value ? 'Root' : '无障碍'}]\n请完全关闭并重启 App 以生效！`);
}

// 2. 同步普通脚本参数
function syncConfig(key: string, val: any) {
  Bridge.setConfig(key, String(val));
}

// 3. 批量同步 (暴露给父组件调用)
function syncAllConfigs() {
  syncConfig('loop_times', loopCount.value);
  syncConfig('enable_boss', enableBoss.value);
  syncConfig('target_color', targetColor.value);
  // Root 模式通常不需要每次运行都同步，因为它是在 App 启动时读取的
  // 但为了保险也可以同步一次
  syncConfig('use_root', useRoot.value ? "true" : "false");

  Bridge.log("配置已同步到 Native 层");
}

// 4. 初始化
onMounted(() => {
  // 这里理想情况是能从 Rust/Android 获取当前配置回显
  // 目前因为没有 getConfig 接口，我们先做简单的初始化同步
  syncAllConfigs();
});

// 5. 暴露方法给 App.vue
defineExpose({
  syncAllConfigs
});
</script>

<style scoped>
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
