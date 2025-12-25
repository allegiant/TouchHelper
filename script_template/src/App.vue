<template>
  <div class="app-container">
    <header>
      <h3>脚本配置</h3>
    </header>

    <div class="config-box">
      <div class="form-item">
        <label>循环次数</label>
        <input type="number" v-model="store.config.loopTimes">
      </div>

      <div class="form-item">
        <label>怪物颜色</label>
        <input type="color" v-model="store.config.monsterColor">
      </div>
    </div>

    <div class="config-box">
      <h4>地图设置</h4>
      <div v-for="(map, index) in store.config.maps" :key="index" class="map-item">
        <span>{{ map.name }}</span>
        <input type="number" v-model="map.id" style="width: 50px;">
        <button @click="store.config.maps.splice(index, 1)" class="btn-del">删除</button>
      </div>
      <button @click="addMap" class="btn-add">➕ 添加地图</button>
    </div>

    <div class="tools-section">
      <button @click="showFontMaker = !showFontMaker" class="btn-toggle">
        {{ showFontMaker ? '🔽 收起工具' : '🛠️ 打开字库制作工具' }}
      </button>

      <div v-if="showFontMaker">
        <FontMaker />
      </div>
    </div>

    <div class="actions">
      <button @click="save" class="btn-primary">💾 保存配置到手机</button>
      <button @click="store.reset" class="btn-secondary">重置</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useConfigStore } from './stores/useConfigStore';
import { Bridge } from './utils/native-bridge';
import { injectScriptConfig } from './utils/script-helper';
import FontMaker from './tools/FontMaker.vue'; // 👈 引入组件

const store = useConfigStore();
const showFontMaker = ref(false); // 控制工具显示的开关

function addMap() {
  store.config.maps.push({
    name: "新地图",
    id: 0,
    x: 11,
    y: 12
  });
}

async function save() {
  try {
    const res = await fetch('/script.js');
    if (!res.ok) throw new Error("未找到编译后的脚本文件");
    const rawScript = await res.text();

    const finalScript = injectScriptConfig(rawScript, store.config);
    Bridge.saveScript(finalScript);

    alert("配置已保存！请点击底部【播放】按钮运行。");
  } catch (e: any) {
    alert("保存失败: " + e.message);
  }
}
</script>

<style scoped>
.app-container {
  padding: 20px;
  max-width: 600px;
  margin: 0 auto;
}

.config-box {
  margin-bottom: 20px;
  padding: 10px;
  border: 1px solid #eee;
  border-radius: 8px;
}

.form-item,
.map-item {
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

button {
  cursor: pointer;
}

/* 按钮样式优化 */
.btn-del {
  background: #ff4d4f;
  color: white;
  border: none;
  border-radius: 4px;
  padding: 4px 8px;
}

.btn-add {
  background: #fff;
  border: 1px dashed #999;
  width: 100%;
  padding: 5px;
}

.btn-primary {
  padding: 10px 20px;
  background: #1890ff;
  color: white;
  border: none;
  border-radius: 4px;
  flex: 1;
}

.btn-secondary {
  padding: 10px 20px;
  background: #999;
  color: white;
  border: none;
  border-radius: 4px;
  margin-left: 10px;
}

.actions {
  display: flex;
  margin-top: 20px;
  position: sticky;
  bottom: 0;
  background: white;
  padding: 10px 0;
  border-top: 1px solid #eee;
}

/* 工具区样式 */
.tools-section {
  margin: 20px 0;
}

.btn-toggle {
  width: 100%;
  background: #f0f5ff;
  border: 1px solid #adc6ff;
  color: #2f54eb;
  padding: 8px;
  border-radius: 4px;
}
</style>
