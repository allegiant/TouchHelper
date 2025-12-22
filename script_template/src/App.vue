<template>
  <div class="app-container">
    <h3>脚本配置</h3>

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

        <button @click="store.config.maps.splice(index, 1)">删除</button>
      </div>

      <button @click="addMap">➕ 添加地图</button>
    </div>

    <div class="actions">
      <button @click="save">💾 保存配置到手机</button>
      <button @click="store.reset" style="background:#666">重置</button>
    </div>
  </div>

</template>

<script setup lang="ts">
import { useConfigStore } from './stores/useConfigStore';
import { Bridge } from './utils/native-bridge';
import { injectScriptConfig } from './utils/script-helper';
const store = useConfigStore();

function addMap() {
  // push 一个符合 MapItem 结构的新对象
  store.config.maps.push({
    name: "新地图",
    id: 0,
    x: 11,
    y: 12
  });
}

async function save() {
  try {
    // 1. 获取基础脚本 (由 tools/dev.js 编译生成的 script.js)
    const res = await fetch('/script.js');
    if (!res.ok) throw new Error("未找到编译后的脚本文件");
    const rawScript = await res.text();

    // 2. 注入当前 UI 配置
    const finalScript = injectScriptConfig(rawScript, store.config);

    // 3. 发送给安卓保存
    Bridge.saveScript(finalScript);

    // 提示用户
    alert("配置已保存！请点击底部【播放】按钮运行。");
  } catch (e: any) {
    alert("保存失败: " + e.message);
  }
}
</script>

<style scoped>
.app-container {
  padding: 20px;
}

.form-item {
  margin-bottom: 15px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

button {
  padding: 10px 20px;
  margin-right: 10px;
  border: none;
  border-radius: 4px;
  color: white;
  background: #1890ff;
}
</style>
