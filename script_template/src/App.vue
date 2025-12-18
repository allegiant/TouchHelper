<template>
  <div class="container">
    <el-card class="header-card">
      <h2>🤖 TouchHelper Script Builder</h2>

      <el-form :inline="true" label-position="left">
        <el-form-item label="循环次数">
          <el-input-number v-model="config.loop_count" :min="1" :max="9999" />
        </el-form-item>
        <el-form-item label="Root模式">
          <el-switch v-model="config.use_root" active-text="开启" inactive-text="关闭" />
        </el-form-item>
      </el-form>

      <div class="actions-bar">
        <el-button type="primary" @click="addClick">➕ 点击</el-button>
        <el-button type="success" @click="addFindColor">🎨 找色</el-button>
        <el-button type="warning" @click="addWait">⏱️ 等待</el-button>
        <el-button type="info" @click="addLog">📝 日志</el-button>
      </div>
    </el-card>

    <div class="script-list">
      <el-empty v-if="config.actions.length === 0" description="暂无动作，请添加" />

      <draggable-list v-else>
        <el-card v-for="(action, index) in config.actions" :key="index" class="action-item">
          <div class="action-header">
            <el-tag>{{ action.type }}</el-tag>
            <el-button type="danger" link @click="removeAction(index)">删除</el-button>
          </div>

          <div v-if="action.type === 'Click'" class="action-editor">
            <el-input-number v-model="action.x" placeholder="X" size="small" />
            <el-input-number v-model="action.y" placeholder="Y" size="small" />
            <span class="label">延时(ms):</span>
            <el-input-number v-model="action.delay_ms" size="small" :step="100" />
          </div>

          <div v-if="action.type === 'Wait'" class="action-editor">
            <span class="label">等待(ms):</span>
            <el-input-number v-model="action.ms" :step="500" />
          </div>

          <div v-if="action.type === 'Log'" class="action-editor">
            <el-input v-model="action.msg" placeholder="输入日志内容" />
          </div>

          <div v-if="action.type === 'FindAndClick'" class="action-editor vertical">
            <div class="row">
              <el-color-picker v-model="action.color_html" />
              <span class="label">色值: {{ action.color_html }}</span>
            </div>
            <div class="row">
              <span class="label">容差:</span>
              <el-slider v-model="action.tolerance" :max="255" style="width: 200px" />
            </div>
          </div>

        </el-card>
      </draggable-list>
    </div>

    <div class="float-btn">
      <el-button type="primary" size="large" circle class="run-btn" @click="runScript">
        ▶
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive } from 'vue';
import { Bridge } from './utils/bridge';
import type { MacroConfig, Action } from './types/touch-helper';

// 初始配置
const config = reactive<MacroConfig>({
  loop_count: 1,
  use_root: true, // 默认开启 Root 模式 (配合 Server)
  actions: []
});

// 添加动作辅助函数
const addClick = () => config.actions.push({ type: 'Click', x: 500, y: 500, delay_ms: 1000 });
const addWait = () => config.actions.push({ type: 'Wait', ms: 2000 });
const addLog = () => config.actions.push({ type: 'Log', msg: '运行中...' });
const addFindColor = () => config.actions.push({
  type: 'FindAndClick',
  color_html: '#ff0000',
  tolerance: 10,
  region: undefined // 全屏找
});

const removeAction = (index: number) => {
  config.actions.splice(index, 1);
};

const runScript = () => {
  Bridge.run(config);
};
</script>

<style scoped>
.container {
  max-width: 600px;
  margin: 0 auto;
  padding-bottom: 80px;
}

.header-card {
  position: sticky;
  top: 0;
  z-index: 10;
  margin-bottom: 20px;
}

.actions-bar {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  padding-bottom: 5px;
}

.action-item {
  margin-bottom: 10px;
}

.action-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 10px;
}

.action-editor {
  display: flex;
  gap: 10px;
  align-items: center;
}

.action-editor.vertical {
  flex-direction: column;
  align-items: flex-start;
}

.row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}

.label {
  font-size: 12px;
  color: #666;
  white-space: nowrap;
}

.float-btn {
  position: fixed;
  bottom: 30px;
  right: 30px;
}

.run-btn {
  width: 60px;
  height: 60px;
  font-size: 24px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
}
</style>
